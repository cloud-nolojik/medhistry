"""QR code session endpoints: generate, refresh, scan, end, and remote share codes."""

from datetime import date
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from sqlalchemy import select

from app.core.database import get_db
from app.models.patient import Patient
from app.models.medical_document import MedicalDocument
from app.schemas.qr import (
    QRGenerateRequest, QRGenerateResponse, QRRefreshResponse, QRScanRequest,
    PatientBriefing,
    ShareCodeGenerateRequest, ShareCodeGenerateResponse, ShareCodeRedeemRequest,
)
from app.services.qr_service import (
    start_qr_session, refresh_qr_token, verify_scanned_qr, end_qr_session,
    start_share_code_session, redeem_share_code, log_patient_access,
)
from app.api.deps import get_current_patient, get_current_doctor, resolve_patient_context
from app.models.doctor import Doctor
from app.models.qr_session import QRSession

router = APIRouter(prefix="/qr", tags=["qr"])


def _calculate_age(dob_str: str | None) -> str | None:
    """Calculate age string from date of birth."""
    if not dob_str:
        return None
    try:
        dob = date.fromisoformat(dob_str)
        today = date.today()
        age = today.year - dob.year - ((today.month, today.day) < (dob.month, dob.day))
        return f"{age} years"
    except ValueError:
        return None


@router.post("/generate", response_model=QRGenerateResponse)
async def generate_qr(
    data: QRGenerateRequest = QRGenerateRequest(),
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Start a new QR sharing session. Patient calls this to begin showing QR.

    If `patient_id` is omitted, the QR session covers the primary's records.
    If `patient_id` is a dependent managed by the primary, it covers that
    dependent's records — same auth model as /generate-code.

    Returns the encrypted QR token to be encoded into a QR code on the client.
    """
    target_id = data.patient_id or primary.id
    target = await resolve_patient_context(target_id, primary, db)
    session = await start_qr_session(target.id, db)
    return QRGenerateResponse(
        session_id=session.id,
        qr_token=session.current_token,
        token_version=session.token_version,
        expires_at=session.expires_at,
        patient_id=target.id,
        patient_name=target.name,
    )


@router.post("/refresh/{session_id}", response_model=QRRefreshResponse)
async def refresh_qr(
    session_id: UUID,
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Rotate the QR token for an active session.

    Called every 60 seconds by the patient app. The old token becomes invalid.
    Works for sessions on the primary's own records OR a dependent's, as
    long as the primary manages the dependent.
    """
    # Look up the session and verify the caller has rights to its patient.
    sess_result = await db.execute(
        select(QRSession).where(QRSession.id == session_id, QRSession.is_active == True)
    )
    existing = sess_result.scalar_one_or_none()
    if not existing:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Session not found, expired, or inactive",
        )
    # Authorize: primary owns this session's patient (self or dependent).
    await resolve_patient_context(existing.patient_id, primary, db)

    session = await refresh_qr_token(session_id, existing.patient_id, db)
    if not session:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Session not found, expired, or inactive",
        )
    return QRRefreshResponse(
        qr_token=session.current_token,
        token_version=session.token_version,
        expires_at=session.expires_at,
    )


async def _build_briefing(
    patient: Patient,
    session: QRSession,
    db: AsyncSession,
) -> PatientBriefing:
    """Shared briefing builder used by both /scan and /redeem-code."""
    doc_result = await db.execute(
        select(MedicalDocument).where(
            MedicalDocument.patient_id == patient.id,
            MedicalDocument.processing_status == "completed",
        ).order_by(MedicalDocument.created_at.desc())
    )
    docs = doc_result.scalars().all()

    all_meds = []
    all_diagnoses = []
    critical_labs = []
    for doc in docs:
        data = doc.extracted_data or {}
        all_diagnoses.extend(data.get("diagnoses", []))
        all_meds.extend(data.get("medications", []))
        for lab in data.get("lab_results", []):
            if lab.get("status") in ("high", "low", "critical"):
                critical_labs.append(lab)

    return PatientBriefing(
        patient_id=patient.id,
        name=patient.name,
        age=_calculate_age(patient.date_of_birth),
        gender=patient.gender,
        blood_group=patient.blood_group,
        allergies=patient.allergies,
        medical_summary=patient.medical_summary,
        medications=all_meds,
        diagnoses=list(set(all_diagnoses)),
        critical_labs=critical_labs,
        total_documents=len(docs),
        session_expires_at=session.expires_at,
    )


@router.post("/scan", response_model=PatientBriefing)
async def scan_qr(
    data: QRScanRequest,
    doctor: Doctor = Depends(get_current_doctor),
    db: AsyncSession = Depends(get_db),
):
    """Doctor scans patient's QR code. Returns the patient briefing card.

    Requires doctor authentication — only verified, hospital-affiliated
    doctors can view patient data. The QR token + doctor JWT together
    form the two-factor access control.
    """
    patient, session = await verify_scanned_qr(data.qr_token, db)
    if not patient or not session:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid, expired, or already-used QR code",
        )

    briefing = await _build_briefing(patient, session, db)
    await log_patient_access(
        db,
        patient_id=patient.id,
        doctor_id=doctor.id,
        hospital_id=doctor.hospital_id,
        method="qr_scan",
    )
    return briefing


@router.post("/generate-code", response_model=ShareCodeGenerateResponse)
async def generate_share_code(
    data: ShareCodeGenerateRequest,
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Generate a 6-digit remote share code for an in-person or remote doctor.

    If `patient_id` is omitted, the code covers the primary's own records.
    If `patient_id` refers to a dependent managed by the primary (Dad, Mom,
    kids), the code covers that dependent's records. Anyone else → 403.

    TTL: 5 minutes, single-use. The primary reads the code to the doctor
    over the phone (or shares via SMS/WhatsApp); doctor redeems from their
    app via POST /qr/redeem-code.
    """
    target_id = data.patient_id or primary.id
    target = await resolve_patient_context(target_id, primary, db)
    session = await start_share_code_session(target.id, db)
    return ShareCodeGenerateResponse(
        session_id=session.id,
        share_code=session.share_code,
        patient_id=target.id,
        patient_name=target.name,
        expires_at=session.expires_at,
    )


@router.post("/redeem-code", response_model=PatientBriefing)
async def redeem_code(
    data: ShareCodeRedeemRequest,
    doctor: Doctor = Depends(get_current_doctor),
    db: AsyncSession = Depends(get_db),
):
    """Doctor redeems a 6-digit share code to get the patient briefing.

    Same output as /scan — the doctor app uses one Patient Access screen
    with both scanner and code input, and routes to whichever endpoint
    matches the input method. Both return the identical PatientBriefing.
    """
    patient, session = await redeem_share_code(data.share_code, db)
    if not patient or not session:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid, expired, or already-used share code",
        )

    briefing = await _build_briefing(patient, session, db)
    await log_patient_access(
        db,
        patient_id=patient.id,
        doctor_id=doctor.id,
        hospital_id=doctor.hospital_id,
        method="share_code",
    )
    return briefing


@router.post("/end/{session_id}", status_code=status.HTTP_204_NO_CONTENT)
async def end_session(
    session_id: UUID,
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Patient manually ends a QR sharing session.

    Works for sessions on the primary's own records or a managed dependent's.
    """
    sess_result = await db.execute(
        select(QRSession).where(QRSession.id == session_id, QRSession.is_active == True)
    )
    existing = sess_result.scalar_one_or_none()
    if not existing:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No active session found",
        )
    await resolve_patient_context(existing.patient_id, primary, db)

    ended = await end_qr_session(session_id, existing.patient_id, db)
    if not ended:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No active session found",
        )
