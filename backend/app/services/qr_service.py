"""QR session management: create, refresh, verify, and expire QR tokens.

Also manages remote-share codes: a 6-digit numeric code the patient (or
their family manager) reads over the phone to a doctor, which the doctor
then redeems from their app to get the same PatientBriefing as an in-person
QR scan.
"""

import secrets
import uuid
from datetime import datetime, timedelta, timezone

from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.security import create_qr_token, verify_qr_token
from app.models.qr_session import QRSession
from app.models.patient import Patient
from app.models.patient_access_log import PatientAccessLog


SHARE_CODE_TTL_MINUTES = 5
SHARE_CODE_DIGITS = 6


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


def _ensure_aware(dt: datetime) -> datetime:
    """Ensure a datetime is timezone-aware (SQLite returns naive datetimes)."""
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt


async def start_qr_session(patient_id: uuid.UUID, db: AsyncSession) -> QRSession:
    """Create a new QR sharing session for a patient.

    Deactivates any existing active sessions first.
    """
    # Deactivate existing sessions
    await db.execute(
        update(QRSession)
        .where(QRSession.patient_id == patient_id, QRSession.is_active == True)
        .values(is_active=False)
    )

    session_id = uuid.uuid4()
    token = create_qr_token(str(patient_id), str(session_id))
    expires_at = _utcnow() + timedelta(minutes=settings.SESSION_EXPIRY_MINUTES)

    qr_session = QRSession(
        id=session_id,
        patient_id=patient_id,
        current_token=token,
        token_version=1,
        is_active=True,
        expires_at=expires_at,
    )
    db.add(qr_session)
    await db.commit()
    await db.refresh(qr_session)
    return qr_session


async def refresh_qr_token(session_id: uuid.UUID, patient_id: uuid.UUID, db: AsyncSession) -> QRSession | None:
    """Generate a new encrypted token for an active QR session.

    Called every 60 seconds by the patient app to rotate the QR code.
    """
    result = await db.execute(
        select(QRSession).where(
            QRSession.id == session_id,
            QRSession.patient_id == patient_id,
            QRSession.is_active == True,
        )
    )
    session = result.scalar_one_or_none()
    if not session:
        return None

    # Check if session has expired
    if _utcnow() > _ensure_aware(session.expires_at):
        session.is_active = False
        await db.commit()
        return None

    # Rotate the token
    new_token = create_qr_token(str(patient_id), str(session_id))
    session.current_token = new_token
    session.token_version += 1
    await db.commit()
    await db.refresh(session)
    return session


async def verify_scanned_qr(token: str, db: AsyncSession) -> tuple[Patient | None, QRSession | None]:
    """Verify a QR token scanned by a doctor.

    Returns the patient and session if valid, (None, None) otherwise.
    """
    payload = verify_qr_token(token)
    if not payload:
        return None, None

    patient_id = payload.get("pid")
    session_id = payload.get("sid")
    if not patient_id or not session_id:
        return None, None

    # Verify session is active AND token matches current rotation
    result = await db.execute(
        select(QRSession).where(
            QRSession.id == uuid.UUID(session_id),
            QRSession.patient_id == uuid.UUID(patient_id),
            QRSession.is_active == True,
            QRSession.current_token == token,  # Must match latest rotation
        )
    )
    session = result.scalar_one_or_none()
    if not session or _utcnow() > _ensure_aware(session.expires_at):
        return None, None

    # Single-use enforcement: if this session was already consumed by a
    # previous scan, reject. Patient must start a brand new session to
    # share again. This blocks token replay even within the 60s window.
    if session.scanned_at is not None:
        return None, None

    # Mark as scanned + immediately deactivate so the same session
    # can't be re-scanned and rotation stops.
    session.scanned_at = _utcnow()
    session.is_active = False
    await db.commit()

    # Fetch patient
    patient_result = await db.execute(
        select(Patient).where(Patient.id == uuid.UUID(patient_id))
    )
    patient = patient_result.scalar_one_or_none()

    return patient, session


async def _generate_unique_share_code(db: AsyncSession) -> str:
    """Generate a 6-digit numeric share code that isn't currently active.

    Uses cryptographic randomness. Collisions are checked only among *active*
    share-code sessions, so codes can be safely reused after expiry.
    """
    for _ in range(10):
        code = f"{secrets.randbelow(10 ** SHARE_CODE_DIGITS):0{SHARE_CODE_DIGITS}d}"
        existing = await db.execute(
            select(QRSession).where(
                QRSession.share_code == code,
                QRSession.is_active == True,
            )
        )
        if existing.scalar_one_or_none() is None:
            return code
    raise RuntimeError("Unable to generate unique share code after 10 tries")


async def start_share_code_session(
    patient_id: uuid.UUID,
    db: AsyncSession,
) -> QRSession:
    """Create a remote share session. Returns the QRSession row with share_code set.

    Deactivates any existing active share-code sessions for this patient first,
    so a new code supersedes the old one. In-person QR sessions are left alone.
    """
    # Kill any previous active share-code session for this patient so a
    # fresh code always supersedes an older one.
    await db.execute(
        update(QRSession)
        .where(
            QRSession.patient_id == patient_id,
            QRSession.is_active == True,
            QRSession.mode == "share_code",
        )
        .values(is_active=False)
    )

    code = await _generate_unique_share_code(db)
    session_id = uuid.uuid4()
    token = create_qr_token(str(patient_id), str(session_id))
    expires_at = _utcnow() + timedelta(minutes=SHARE_CODE_TTL_MINUTES)

    session = QRSession(
        id=session_id,
        patient_id=patient_id,
        current_token=token,
        token_version=1,
        is_active=True,
        expires_at=expires_at,
        share_code=code,
        mode="share_code",
    )
    db.add(session)
    await db.commit()
    await db.refresh(session)
    return session


async def redeem_share_code(
    code: str,
    db: AsyncSession,
) -> tuple[Patient | None, QRSession | None]:
    """Redeem a 6-digit share code, returning the patient and session.

    Single-use: immediately marks scanned_at + deactivates the session on
    the first successful redemption, so replays return (None, None).
    """
    if not code or len(code) != SHARE_CODE_DIGITS or not code.isdigit():
        return None, None

    result = await db.execute(
        select(QRSession).where(
            QRSession.share_code == code,
            QRSession.is_active == True,
            QRSession.mode == "share_code",
        )
    )
    session = result.scalar_one_or_none()
    if not session:
        return None, None
    if _utcnow() > _ensure_aware(session.expires_at):
        session.is_active = False
        await db.commit()
        return None, None
    if session.scanned_at is not None:
        return None, None

    # Consume it
    session.scanned_at = _utcnow()
    session.is_active = False
    await db.commit()

    patient_result = await db.execute(
        select(Patient).where(Patient.id == session.patient_id)
    )
    patient = patient_result.scalar_one_or_none()
    return patient, session


async def log_patient_access(
    db: AsyncSession,
    *,
    patient_id: uuid.UUID,
    doctor_id: uuid.UUID,
    hospital_id: uuid.UUID | None,
    method: str,
) -> PatientAccessLog:
    """Record a successful doctor access to a patient's records."""
    entry = PatientAccessLog(
        patient_id=patient_id,
        doctor_id=doctor_id,
        hospital_id=hospital_id,
        method=method,
    )
    db.add(entry)
    await db.commit()
    await db.refresh(entry)
    return entry


async def end_qr_session(session_id: uuid.UUID, patient_id: uuid.UUID, db: AsyncSession) -> bool:
    """Manually end a QR session (patient closes sharing)."""
    result = await db.execute(
        update(QRSession)
        .where(
            QRSession.id == session_id,
            QRSession.patient_id == patient_id,
            QRSession.is_active == True,
        )
        .values(is_active=False)
    )
    await db.commit()
    return result.rowcount > 0
