"""Doctor auth — OTP-based (mirrors patient flow).

Flow:
  1. POST /doctors/send-otp              → generates 4-digit OTP for the phone
  2. POST /doctors/verify-otp            → verifies OTP
       - existing doctor: returns access_token + doctor (logged in)
       - new doctor:      returns temp_token (for use in step 3)
  3. POST /doctors/complete-registration → temp_token + invite_code → account

There is no password for doctors. Doctors log in with phone + OTP on every
device sign-in.

Also exposes:
  - GET /doctors/verify-invite/{code}  → checks an invite code pre-registration
  - GET /doctors/me                    → current doctor profile
  - GET /doctors/me/dashboard          → stats + recent briefings for home
"""

import random
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import create_access_token, decode_token
from app.models.doctor import Doctor
from app.models.hospital import Hospital
from app.models.invitation import Invitation, InvitationStatus
from app.models.otp import OTP
from app.models.patient import Patient
from app.models.patient_access_log import PatientAccessLog
from app.schemas.doctor import (
    DoctorSendOTPRequest,
    DoctorSendOTPResponse,
    DoctorVerifyOTPRequest,
    DoctorVerifyOTPResponse,
    DoctorVerifyInviteRequest,
    DoctorVerifyInviteResponse,
    DoctorCompleteRegistration,
    DoctorOut,
    DoctorTokenResponse,
    DoctorDashboard,
    DoctorDashboardBriefing,
    DoctorBriefingsList,
    DoctorSetPinRequest,
    DoctorPinLoginRequest,
)
from app.api.deps import get_current_doctor
from app.core.security import hash_password, verify_password

PIN_MAX_ATTEMPTS = 5

router = APIRouter(prefix="/doctors", tags=["doctors"])

OTP_EXPIRY_SECONDS = 300  # 5 minutes
OTP_MAX_ATTEMPTS = 5
TEMP_TOKEN_EXPIRY = timedelta(minutes=10)
TEMP_TOKEN_PREFIX = "doctor-otp-verified:"


def _ensure_aware(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt


async def _doctor_out_with_hospital(doctor: Doctor, db: AsyncSession) -> DoctorOut:
    """Build a DoctorOut including the hospital name + has_pin."""
    out = DoctorOut.model_validate(doctor)
    out.has_pin = doctor.pin_hash is not None
    hospital_result = await db.execute(
        select(Hospital).where(Hospital.id == doctor.hospital_id)
    )
    hospital = hospital_result.scalar_one_or_none()
    if hospital:
        out.hospital_name = hospital.name
    return out


# ===================== INVITE VERIFICATION =====================


@router.get("/verify-invite/{invite_code}")
async def verify_invite_code(invite_code: str, db: AsyncSession = Depends(get_db)):
    """Verify an invite code pre-registration. Returns hospital + pre-fill data."""
    result = await db.execute(
        select(Invitation).where(
            Invitation.invite_code == invite_code,
            Invitation.status == InvitationStatus.PENDING,
        )
    )
    invitation = result.scalar_one_or_none()
    if not invitation:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Invalid or expired invitation code",
        )

    if datetime.now(timezone.utc) > _ensure_aware(invitation.expires_at):
        invitation.status = InvitationStatus.EXPIRED
        await db.commit()
        raise HTTPException(
            status_code=status.HTTP_410_GONE,
            detail="Invitation has expired",
        )

    hospital_result = await db.execute(
        select(Hospital).where(Hospital.id == invitation.hospital_id)
    )
    hospital = hospital_result.scalar_one_or_none()
    hospital_name = hospital.name if hospital else "Unknown Hospital"

    return {
        "valid": True,
        "hospital_name": hospital_name,
        "doctor_name": invitation.doctor_name,
        "specialisation": invitation.specialisation,
        "doctor_phone": invitation.doctor_phone,
    }


@router.post("/verify-invite", response_model=DoctorVerifyInviteResponse)
async def verify_invite_with_phone(
    data: DoctorVerifyInviteRequest,
    db: AsyncSession = Depends(get_db),
):
    """Verify an invite code AND that it matches the OTP-verified phone.

    Rejects immediately with a clear message when the hospital admin issued
    the invite to a different phone — saves the doctor from filling out the
    whole signup form before finding out.
    """
    # Decode temp_token → OTP-verified phone
    payload = decode_token(data.temp_token)
    if not payload or not str(payload.get("sub", "")).startswith(TEMP_TOKEN_PREFIX):
        raise HTTPException(
            status_code=401,
            detail="Phone verification expired. Please re-enter your phone and OTP.",
        )
    verified_phone = str(payload["sub"]).replace(TEMP_TOKEN_PREFIX, "")

    # Find invitation
    result = await db.execute(
        select(Invitation).where(
            Invitation.invite_code == data.invite_code,
            Invitation.status == InvitationStatus.PENDING,
        )
    )
    invitation = result.scalar_one_or_none()
    if not invitation:
        raise HTTPException(
            status_code=404,
            detail="Invalid or already-used invitation code",
        )

    if datetime.now(timezone.utc) > _ensure_aware(invitation.expires_at):
        invitation.status = InvitationStatus.EXPIRED
        await db.commit()
        raise HTTPException(status_code=410, detail="Invitation has expired")

    # Fail-fast: phone must match what the admin entered
    if invitation.doctor_phone != verified_phone:
        raise HTTPException(
            status_code=400,
            detail="This invite code was issued to a different phone number. "
                   "Please check the code or contact your hospital admin.",
        )

    hospital_result = await db.execute(
        select(Hospital).where(Hospital.id == invitation.hospital_id)
    )
    hospital = hospital_result.scalar_one_or_none()
    hospital_name = hospital.name if hospital else "Unknown Hospital"

    return DoctorVerifyInviteResponse(
        valid=True,
        hospital_name=hospital_name,
        doctor_name=invitation.doctor_name,
        specialisation=invitation.specialisation,
        doctor_phone=invitation.doctor_phone,
    )


# ===================== OTP FLOW =====================


@router.post("/send-otp", response_model=DoctorSendOTPResponse)
async def send_otp(data: DoctorSendOTPRequest, db: AsyncSession = Depends(get_db)):
    """Generate a 4-digit OTP for a doctor phone number."""
    # Invalidate any existing unused OTPs for this phone
    existing = await db.execute(
        select(OTP).where(OTP.phone == data.phone, OTP.is_used == False)
    )
    for old_otp in existing.scalars().all():
        old_otp.is_used = True

    code = f"{random.randint(0, 9999):04d}"
    otp = OTP(
        phone=data.phone,
        code=code,
        expires_at=datetime.now(timezone.utc) + timedelta(seconds=OTP_EXPIRY_SECONDS),
    )
    db.add(otp)
    await db.commit()

    print(f"\n{'='*50}")
    print(f"🩺 Doctor OTP for {data.phone}: {code}")
    print(f"{'='*50}\n")

    return DoctorSendOTPResponse(
        message="OTP sent",
        expires_in_seconds=OTP_EXPIRY_SECONDS,
        otp=code,  # DEV — remove once SMS is wired up
    )


@router.post("/verify-otp", response_model=DoctorVerifyOTPResponse)
async def verify_otp(data: DoctorVerifyOTPRequest, db: AsyncSession = Depends(get_db)):
    """Verify OTP. Returns either a logged-in token (existing doctor)
    or a temp_token for completing registration (new doctor).
    """
    # Latest unused OTP for this phone
    otp_result = await db.execute(
        select(OTP)
        .where(OTP.phone == data.phone, OTP.is_used == False)
        .order_by(OTP.created_at.desc())
        .limit(1)
    )
    otp = otp_result.scalar_one_or_none()

    if not otp:
        raise HTTPException(status_code=400, detail="No OTP found for this number. Request a new one.")

    if datetime.now(timezone.utc) > _ensure_aware(otp.expires_at):
        otp.is_used = True
        await db.commit()
        raise HTTPException(status_code=400, detail="OTP has expired. Request a new one.")

    if otp.attempts >= OTP_MAX_ATTEMPTS:
        otp.is_used = True
        await db.commit()
        raise HTTPException(status_code=429, detail="Too many attempts. Request a new OTP.")

    if otp.code != data.otp:
        otp.attempts += 1
        await db.commit()
        remaining = OTP_MAX_ATTEMPTS - otp.attempts
        raise HTTPException(
            status_code=400,
            detail=f"Invalid OTP. {remaining} attempt{'s' if remaining != 1 else ''} remaining.",
        )

    # OTP valid
    otp.is_used = True
    await db.commit()

    # Does a doctor already exist for this phone?
    doctor_result = await db.execute(select(Doctor).where(Doctor.phone == data.phone))
    existing = doctor_result.scalar_one_or_none()

    if existing is not None:
        # Successful OTP login — reset any locked-out PIN counter. If this was
        # a Forgot-PIN flow the caller will clear the old hash via /set-pin.
        if existing.pin_failed_attempts:
            existing.pin_failed_attempts = 0
            await db.commit()
        token = create_access_token(f"doctor:{existing.id}")
        return DoctorVerifyOTPResponse(
            verified=True,
            is_new_user=False,
            access_token=token,
            doctor=await _doctor_out_with_hospital(existing, db),
            temp_token=None,
        )

    # New doctor — issue a short-lived temp token for complete-registration
    temp_token = create_access_token(
        subject=f"{TEMP_TOKEN_PREFIX}{data.phone}",
        expires_delta=TEMP_TOKEN_EXPIRY,
    )
    return DoctorVerifyOTPResponse(
        verified=True,
        is_new_user=True,
        access_token=None,
        doctor=None,
        temp_token=temp_token,
    )


# ===================== NEW USER REGISTRATION =====================


@router.post(
    "/complete-registration",
    response_model=DoctorTokenResponse,
    status_code=status.HTTP_201_CREATED,
)
async def complete_registration(
    data: DoctorCompleteRegistration,
    db: AsyncSession = Depends(get_db),
):
    """Create a doctor account after OTP verification. Requires a valid pending
    invitation whose phone matches the OTP-verified phone from the temp_token.
    """
    payload = decode_token(data.temp_token)
    if not payload or not str(payload.get("sub", "")).startswith(TEMP_TOKEN_PREFIX):
        raise HTTPException(status_code=401, detail="Invalid or expired verification token")

    phone = str(payload["sub"]).replace(TEMP_TOKEN_PREFIX, "")

    # Check phone not already registered as a doctor
    existing = await db.execute(select(Doctor).where(Doctor.phone == phone))
    if existing.scalar_one_or_none():
        raise HTTPException(status_code=409, detail="Phone number already registered")

    # Find invitation
    inv_result = await db.execute(
        select(Invitation).where(
            Invitation.invite_code == data.invite_code,
            Invitation.status == InvitationStatus.PENDING,
        )
    )
    invitation = inv_result.scalar_one_or_none()
    if not invitation:
        raise HTTPException(status_code=404, detail="Invalid or expired invitation code")

    if datetime.now(timezone.utc) > _ensure_aware(invitation.expires_at):
        invitation.status = InvitationStatus.EXPIRED
        await db.commit()
        raise HTTPException(status_code=410, detail="Invitation has expired")

    # Invitation phone must match the OTP-verified phone
    if invitation.doctor_phone != phone:
        raise HTTPException(
            status_code=400,
            detail="This invitation was issued for a different phone number",
        )

    # Build account (prefer user-supplied overrides; fall back to invitation data)
    doctor = Doctor(
        hospital_id=invitation.hospital_id,
        phone=phone,
        name=(data.name or invitation.doctor_name or "").strip() or "Doctor",
        # No password — OTP-only flow. Keep a placeholder hash so existing
        # column NOT NULL constraints are satisfied; it is never used.
        password_hash="!otp-only",
        specialisation=data.specialisation or invitation.specialisation,
        license_number=data.license_number,
        email=data.email,
    )
    db.add(doctor)

    invitation.status = InvitationStatus.ACCEPTED
    invitation.accepted_at = datetime.now(timezone.utc)

    await db.commit()
    await db.refresh(doctor)

    token = create_access_token(f"doctor:{doctor.id}")
    return DoctorTokenResponse(
        access_token=token,
        doctor=await _doctor_out_with_hospital(doctor, db),
    )


# ===================== PIN AUTH =====================


@router.post("/set-pin", response_model=DoctorOut)
async def set_pin(
    data: DoctorSetPinRequest,
    doctor: Doctor = Depends(get_current_doctor),
    db: AsyncSession = Depends(get_db),
):
    """Set or replace the doctor's 6-digit PIN. Requires a valid bearer token
    (i.e. the doctor just OTP-verified). Also called from the Forgot-PIN flow
    after a fresh OTP — the re-OTP login gives a new token, caller then sets a
    new PIN with it.
    """
    if not data.pin.isdigit() or len(data.pin) != 6:
        raise HTTPException(status_code=400, detail="PIN must be exactly 6 digits")

    doctor.pin_hash = hash_password(data.pin)
    doctor.pin_failed_attempts = 0
    await db.commit()
    await db.refresh(doctor)

    return await _doctor_out_with_hospital(doctor, db)


@router.post("/pin-login", response_model=DoctorTokenResponse)
async def pin_login(
    data: DoctorPinLoginRequest,
    db: AsyncSession = Depends(get_db),
):
    """Log a doctor in with phone + 6-digit PIN (fast path on re-open).

    After PIN_MAX_ATTEMPTS wrong tries the PIN is cleared — the doctor must
    re-verify via OTP ("Forgot PIN") and set a new one.
    """
    result = await db.execute(select(Doctor).where(Doctor.phone == data.phone))
    doctor = result.scalar_one_or_none()

    # Uniform error text — don't leak whether the phone exists
    if not doctor or not doctor.pin_hash:
        raise HTTPException(status_code=401, detail="Invalid phone or PIN")

    if doctor.pin_failed_attempts >= PIN_MAX_ATTEMPTS:
        # PIN is locked — force re-OTP recovery
        doctor.pin_hash = None
        doctor.pin_failed_attempts = 0
        await db.commit()
        raise HTTPException(
            status_code=429,
            detail="Too many wrong PIN attempts. Please sign in with OTP to reset your PIN.",
        )

    if not verify_password(data.pin, doctor.pin_hash):
        doctor.pin_failed_attempts += 1
        remaining = PIN_MAX_ATTEMPTS - doctor.pin_failed_attempts
        await db.commit()
        if remaining <= 0:
            raise HTTPException(
                status_code=429,
                detail="Too many wrong PIN attempts. Please sign in with OTP to reset your PIN.",
            )
        raise HTTPException(
            status_code=401,
            detail=f"Wrong PIN. {remaining} attempt{'s' if remaining != 1 else ''} remaining.",
        )

    # Success — reset counter and issue a fresh access token
    doctor.pin_failed_attempts = 0
    await db.commit()

    token = create_access_token(f"doctor:{doctor.id}")
    return DoctorTokenResponse(
        access_token=token,
        doctor=await _doctor_out_with_hospital(doctor, db),
    )


# ===================== PROFILE + DASHBOARD =====================


@router.get("/me", response_model=DoctorOut)
async def get_doctor_profile(
    doctor: Doctor = Depends(get_current_doctor),
    db: AsyncSession = Depends(get_db),
):
    return await _doctor_out_with_hospital(doctor, db)


@router.get("/me/dashboard", response_model=DoctorDashboard)
async def get_doctor_dashboard(
    doctor: Doctor = Depends(get_current_doctor),
    db: AsyncSession = Depends(get_db),
):
    now = datetime.now(timezone.utc)
    start_of_today = datetime(now.year, now.month, now.day, tzinfo=timezone.utc)
    start_of_week = start_of_today - timedelta(days=start_of_today.weekday())

    today_count = (
        await db.execute(
            select(func.count())
            .select_from(PatientAccessLog)
            .where(
                PatientAccessLog.doctor_id == doctor.id,
                PatientAccessLog.accessed_at >= start_of_today,
            )
        )
    ).scalar_one() or 0

    week_count = (
        await db.execute(
            select(func.count())
            .select_from(PatientAccessLog)
            .where(
                PatientAccessLog.doctor_id == doctor.id,
                PatientAccessLog.accessed_at >= start_of_week,
            )
        )
    ).scalar_one() or 0

    all_time_count = (
        await db.execute(
            select(func.count())
            .select_from(PatientAccessLog)
            .where(PatientAccessLog.doctor_id == doctor.id)
        )
    ).scalar_one() or 0

    recent_rows = (
        await db.execute(
            select(PatientAccessLog, Patient.name)
            .join(Patient, Patient.id == PatientAccessLog.patient_id)
            .where(PatientAccessLog.doctor_id == doctor.id)
            .order_by(PatientAccessLog.accessed_at.desc())
            .limit(10)
        )
    ).all()

    return DoctorDashboard(
        today_count=today_count,
        week_count=week_count,
        all_time_count=all_time_count,
        avg_briefing_seconds=None,
        recent_briefings=[
            DoctorDashboardBriefing(
                id=log.id,
                patient_id=log.patient_id,
                patient_name=name,
                method=log.method,
                accessed_at=log.accessed_at,
            )
            for log, name in recent_rows
        ],
    )


@router.get("/me/briefings", response_model=DoctorBriefingsList)
async def list_doctor_briefings(
    days: int | None = Query(
        default=None,
        ge=1,
        le=3650,
        description="Limit to briefings in the last N days. Omit for all-time.",
    ),
    method: str | None = Query(
        default=None,
        description="Filter by method ('qr_scan' or 'share_code'). Omit for both.",
    ),
    search: str | None = Query(
        default=None,
        max_length=120,
        description="Case-insensitive substring match on patient name.",
    ),
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
    doctor: Doctor = Depends(get_current_doctor),
    db: AsyncSession = Depends(get_db),
):
    """Paginated + filterable list of the doctor's briefings.

    Powers the "All Patients" screen with timeline/grouping on the client.
    Filters by date window, method, and patient-name search.
    """
    now = datetime.now(timezone.utc)

    filters = [PatientAccessLog.doctor_id == doctor.id]
    if days is not None:
        filters.append(PatientAccessLog.accessed_at >= now - timedelta(days=days))
    if method:
        filters.append(PatientAccessLog.method == method)

    base_stmt = (
        select(PatientAccessLog, Patient.name)
        .join(Patient, Patient.id == PatientAccessLog.patient_id)
        .where(*filters)
    )
    if search:
        base_stmt = base_stmt.where(Patient.name.ilike(f"%{search.strip()}%"))

    # total count (with same filters)
    count_stmt = select(func.count()).select_from(base_stmt.subquery())
    total = (await db.execute(count_stmt)).scalar_one() or 0

    rows = (
        await db.execute(
            base_stmt
            .order_by(PatientAccessLog.accessed_at.desc())
            .offset(offset)
            .limit(limit)
        )
    ).all()

    return DoctorBriefingsList(
        briefings=[
            DoctorDashboardBriefing(
                id=log.id,
                patient_id=log.patient_id,
                patient_name=name,
                method=log.method,
                accessed_at=log.accessed_at,
            )
            for log, name in rows
        ],
        total=total,
        has_more=(offset + len(rows)) < total,
    )
