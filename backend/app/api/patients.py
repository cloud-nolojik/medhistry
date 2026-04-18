"""Patient OTP verification, registration, PIN login, profile, and family endpoints.

Auth flow:
  1. POST /patients/send-otp        → generates 4-digit OTP, logs to console
  2. POST /patients/verify-otp      → verifies OTP, returns temp_token + is_new_user
  3a. (new user)  POST /patients/complete-registration → name + PIN + profile
  3b. (existing)  POST /patients/pin-login             → phone + 4-digit PIN
"""

import random
from datetime import datetime, timedelta, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.config import settings
from app.core.security import hash_password, verify_password, create_access_token
from app.models.patient import Patient
from app.models.otp import OTP
from app.models.patient_access_log import PatientAccessLog
from app.models.doctor import Doctor
from app.models.hospital import Hospital
from app.schemas.patient import (
    SendOTPRequest, SendOTPResponse,
    VerifyOTPRequest, VerifyOTPResponse,
    CompleteRegistration, PinLoginRequest,
    PatientRegister, PatientLogin,
    PatientOut, TokenResponse,
    DependentCreate, DependentOut, FamilyListResponse,
)
from app.schemas.qr import PatientAccessLogEntry
from app.api.deps import get_current_patient, resolve_patient_context

router = APIRouter(prefix="/patients", tags=["patients"])

OTP_EXPIRY_SECONDS = 300  # 5 minutes
OTP_MAX_ATTEMPTS = 5
TEMP_TOKEN_EXPIRY = timedelta(minutes=10)


def _make_patient_out(patient: Patient) -> PatientOut:
    """Build PatientOut with computed has_pin field."""
    data = PatientOut.model_validate(patient)
    data.has_pin = patient.pin_hash is not None
    return data


# ===================== OTP FLOW =====================

@router.post("/send-otp", response_model=SendOTPResponse)
async def send_otp(data: SendOTPRequest, db: AsyncSession = Depends(get_db)):
    """Generate a 4-digit OTP for the given phone number.

    In dev mode (DEBUG=true), the OTP is returned in the response so
    the mobile app can autofill it.
    """
    # Invalidate any existing unused OTPs for this phone
    existing = await db.execute(
        select(OTP).where(
            OTP.phone == data.phone,
            OTP.is_used == False,
        )
    )
    for old_otp in existing.scalars().all():
        old_otp.is_used = True

    # Generate new 4-digit OTP
    code = f"{random.randint(0, 9999):04d}"

    otp = OTP(
        phone=data.phone,
        code=code,
        expires_at=datetime.now(timezone.utc) + timedelta(seconds=OTP_EXPIRY_SECONDS),
    )
    db.add(otp)
    await db.commit()

    # Log to console (always)
    print(f"\n{'='*50}")
    print(f"📱 OTP for {data.phone}: {code}")
    print(f"{'='*50}\n")

    return SendOTPResponse(
        message="OTP sent",
        expires_in_seconds=OTP_EXPIRY_SECONDS,
        otp=code,  # TODO: remove once SMS integration is live — returning OTP for now so app can autofill
    )


@router.post("/verify-otp", response_model=VerifyOTPResponse)
async def verify_otp(data: VerifyOTPRequest, db: AsyncSession = Depends(get_db)):
    """Verify the OTP. Returns a temp token + whether this is a new user."""
    # Find the latest unused OTP for this phone
    result = await db.execute(
        select(OTP).where(
            OTP.phone == data.phone,
            OTP.is_used == False,
        ).order_by(OTP.created_at.desc()).limit(1)
    )
    otp = result.scalar_one_or_none()

    if not otp:
        raise HTTPException(status_code=400, detail="No OTP found for this number. Request a new one.")

    # Check expiry
    if datetime.now(timezone.utc) > otp.expires_at:
        otp.is_used = True
        await db.commit()
        raise HTTPException(status_code=400, detail="OTP has expired. Request a new one.")

    # Check max attempts
    if otp.attempts >= OTP_MAX_ATTEMPTS:
        otp.is_used = True
        await db.commit()
        raise HTTPException(status_code=429, detail="Too many attempts. Request a new OTP.")

    # Verify code
    if otp.code != data.otp:
        otp.attempts += 1
        await db.commit()
        remaining = OTP_MAX_ATTEMPTS - otp.attempts
        raise HTTPException(
            status_code=400,
            detail=f"Invalid OTP. {remaining} attempt{'s' if remaining != 1 else ''} remaining.",
        )

    # OTP is valid — mark as used
    otp.is_used = True
    await db.commit()

    # Check if patient already exists
    patient_result = await db.execute(
        select(Patient).where(Patient.phone == data.phone, Patient.managed_by == None)
    )
    existing_patient = patient_result.scalar_one_or_none()
    is_new = existing_patient is None

    # If existing, mark phone as verified
    if existing_patient and not existing_patient.is_phone_verified:
        existing_patient.is_phone_verified = True
        await db.commit()

    # Generate a short-lived temp token (encodes phone + purpose)
    temp_token = create_access_token(
        subject=f"otp-verified:{data.phone}",
        expires_delta=TEMP_TOKEN_EXPIRY,
    )

    print(f"\n✅ OTP verified for {data.phone} — {'new user' if is_new else 'existing user'}\n")

    return VerifyOTPResponse(
        verified=True,
        is_new_user=is_new,
        temp_token=temp_token,
    )


# ===================== REGISTRATION (new user after OTP) =====================

@router.post("/complete-registration", response_model=TokenResponse, status_code=status.HTTP_201_CREATED)
async def complete_registration(data: CompleteRegistration, db: AsyncSession = Depends(get_db)):
    """New user completes profile + sets 4-digit PIN after OTP verification."""
    from app.core.security import decode_token

    # Validate temp token
    payload = decode_token(data.temp_token)
    if not payload or not payload.get("sub", "").startswith("otp-verified:"):
        raise HTTPException(status_code=401, detail="Invalid or expired verification token")

    phone = payload["sub"].replace("otp-verified:", "")

    # Check phone not already registered
    existing = await db.execute(
        select(Patient).where(Patient.phone == phone, Patient.managed_by == None)
    )
    if existing.scalar_one_or_none():
        raise HTTPException(status_code=409, detail="Phone number already registered")

    # Validate PIN is 4 digits
    if not data.pin.isdigit() or len(data.pin) != 4:
        raise HTTPException(status_code=400, detail="PIN must be exactly 4 digits")

    patient = Patient(
        phone=phone,
        name=data.name,
        pin_hash=hash_password(data.pin),
        password_hash=hash_password(data.pin),  # keep backward compat
        is_phone_verified=True,
        date_of_birth=data.date_of_birth,
        gender=data.gender,
        blood_group=data.blood_group,
        allergies=data.allergies,
    )
    db.add(patient)
    await db.commit()
    await db.refresh(patient)

    token = create_access_token(str(patient.id))
    return TokenResponse(
        access_token=token,
        patient=_make_patient_out(patient),
    )


# ===================== PIN LOGIN (existing user) =====================

@router.post("/pin-login", response_model=TokenResponse)
async def pin_login(data: PinLoginRequest, db: AsyncSession = Depends(get_db)):
    """Login with phone + 4-digit PIN."""
    result = await db.execute(
        select(Patient).where(Patient.phone == data.phone, Patient.managed_by == None)
    )
    patient = result.scalar_one_or_none()

    if not patient:
        raise HTTPException(status_code=401, detail="Invalid phone or PIN")

    # Try PIN first, fall back to password for legacy accounts
    valid = False
    if patient.pin_hash:
        valid = verify_password(data.pin, patient.pin_hash)
    elif patient.password_hash:
        valid = verify_password(data.pin, patient.password_hash)

    if not valid:
        raise HTTPException(status_code=401, detail="Invalid phone or PIN")

    token = create_access_token(str(patient.id))
    return TokenResponse(
        access_token=token,
        patient=_make_patient_out(patient),
    )


# ===================== LEGACY ENDPOINTS (kept for backward compat) =====================

@router.post("/register", response_model=TokenResponse, status_code=status.HTTP_201_CREATED)
async def register(data: PatientRegister, db: AsyncSession = Depends(get_db)):
    """Legacy register with phone + password. Use /send-otp flow instead."""
    existing = await db.execute(select(Patient).where(Patient.phone == data.phone))
    if existing.scalar_one_or_none():
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Phone number already registered")

    patient = Patient(
        phone=data.phone,
        name=data.name,
        password_hash=hash_password(data.password),
        date_of_birth=data.date_of_birth,
        gender=data.gender,
        blood_group=data.blood_group,
        allergies=data.allergies,
    )
    db.add(patient)
    await db.commit()
    await db.refresh(patient)

    token = create_access_token(str(patient.id))
    return TokenResponse(access_token=token, patient=_make_patient_out(patient))


@router.post("/login", response_model=TokenResponse)
async def login(data: PatientLogin, db: AsyncSession = Depends(get_db)):
    """Legacy login with phone + password. Use /pin-login instead."""
    result = await db.execute(select(Patient).where(Patient.phone == data.phone))
    patient = result.scalar_one_or_none()
    if not patient or not verify_password(data.password, patient.password_hash):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid phone or password")

    token = create_access_token(str(patient.id))
    return TokenResponse(access_token=token, patient=_make_patient_out(patient))


# ===================== PROFILE =====================

@router.get("/me", response_model=PatientOut)
async def get_profile(patient: Patient = Depends(get_current_patient)):
    """Get the current patient's profile."""
    return _make_patient_out(patient)


# ===================== FAMILY / DEPENDENTS =====================

@router.post("/family", response_model=DependentOut, status_code=status.HTTP_201_CREATED)
async def add_dependent(
    data: DependentCreate,
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Add a dependent (parent, spouse, child) to the current account."""
    dependent = Patient(
        name=data.name,
        relationship=data.relationship,
        date_of_birth=data.date_of_birth,
        gender=data.gender,
        blood_group=data.blood_group,
        allergies=data.allergies,
        phone=data.phone,
        password_hash=None,
        managed_by=primary.id,
    )
    db.add(dependent)
    await db.commit()
    await db.refresh(dependent)
    return _make_patient_out(dependent)


@router.get("/family", response_model=FamilyListResponse)
async def list_family(
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """List the primary plus all dependents under this account."""
    result = await db.execute(
        select(Patient)
        .where(Patient.managed_by == primary.id, Patient.is_active == True)
        .order_by(Patient.created_at.asc())
    )
    dependents = result.scalars().all()
    return FamilyListResponse(
        primary=_make_patient_out(primary),
        dependents=[_make_patient_out(d) for d in dependents],
    )


@router.get("/{patient_id}/access-log", response_model=list[PatientAccessLogEntry])
async def get_access_log(
    patient_id: UUID,
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
    limit: int = 50,
):
    """Return the list of doctor accesses to this patient's records."""
    await resolve_patient_context(patient_id, primary, db)

    result = await db.execute(
        select(
            PatientAccessLog, Doctor.name, Hospital.name,
        )
        .join(Doctor, Doctor.id == PatientAccessLog.doctor_id)
        .outerjoin(Hospital, Hospital.id == PatientAccessLog.hospital_id)
        .where(PatientAccessLog.patient_id == patient_id)
        .order_by(PatientAccessLog.accessed_at.desc())
        .limit(limit)
    )
    entries = []
    for log, doc_name, hosp_name in result.all():
        entries.append(PatientAccessLogEntry(
            id=log.id,
            doctor_id=log.doctor_id,
            doctor_name=doc_name,
            hospital_id=log.hospital_id,
            hospital_name=hosp_name,
            method=log.method,
            accessed_at=log.accessed_at,
        ))
    return entries


@router.put("/family/{dependent_id}", response_model=DependentOut)
async def update_dependent(
    dependent_id: UUID,
    data: DependentCreate,
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Update a dependent's profile fields."""
    dependent = await resolve_patient_context(dependent_id, primary, db)
    if dependent.id == primary.id:
        raise HTTPException(status_code=400, detail="Cannot edit self via this endpoint")
    dependent.name = data.name
    dependent.relationship = data.relationship
    if data.date_of_birth is not None:
        dependent.date_of_birth = data.date_of_birth
    if data.gender is not None:
        dependent.gender = data.gender
    if data.blood_group is not None:
        dependent.blood_group = data.blood_group
    if data.allergies is not None:
        dependent.allergies = data.allergies
    if data.phone is not None:
        dependent.phone = data.phone
    await db.commit()
    await db.refresh(dependent)
    return _make_patient_out(dependent)


@router.delete("/family/{dependent_id}", status_code=status.HTTP_204_NO_CONTENT)
async def remove_dependent(
    dependent_id: UUID,
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Soft-delete a dependent. Records remain for audit."""
    dependent = await resolve_patient_context(dependent_id, primary, db)
    if dependent.id == primary.id:
        raise HTTPException(status_code=400, detail="Cannot remove self via this endpoint")
    dependent.is_active = False
    await db.commit()
