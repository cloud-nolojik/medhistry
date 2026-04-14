"""Doctor registration (via invitation), login, and profile endpoints."""

from datetime import datetime, timedelta, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import hash_password, verify_password, create_access_token
from app.models.doctor import Doctor
from app.models.hospital import Hospital
from app.models.invitation import Invitation, InvitationStatus
from app.models.patient import Patient
from app.models.patient_access_log import PatientAccessLog
from app.schemas.doctor import (
    DoctorRegisterViaInvite,
    DoctorLogin,
    DoctorOut,
    DoctorTokenResponse,
    DoctorDashboard,
    DoctorDashboardBriefing,
)
from app.api.deps import get_current_doctor

router = APIRouter(prefix="/doctors", tags=["doctors"])


def _ensure_aware(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt


async def _doctor_out_with_hospital(doctor: Doctor, db: AsyncSession) -> DoctorOut:
    """Build a DoctorOut response including the hospital name (join avoided
    on the fly to keep the API surface simple)."""
    out = DoctorOut.model_validate(doctor)
    hospital_result = await db.execute(
        select(Hospital).where(Hospital.id == doctor.hospital_id)
    )
    hospital = hospital_result.scalar_one_or_none()
    if hospital:
        out.hospital_name = hospital.name
    return out


@router.get("/verify-invite/{invite_code}")
async def verify_invite_code(invite_code: str, db: AsyncSession = Depends(get_db)):
    """Verify an invite code is valid and return the hospital name.

    Called by the doctor app before proceeding to signup.
    """
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

    # Check not expired
    if datetime.now(timezone.utc) > _ensure_aware(invitation.expires_at):
        invitation.status = InvitationStatus.EXPIRED
        await db.commit()
        raise HTTPException(
            status_code=status.HTTP_410_GONE,
            detail="Invitation has expired",
        )

    # Get hospital name
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


@router.post("/register", response_model=DoctorTokenResponse, status_code=status.HTTP_201_CREATED)
async def register_doctor(data: DoctorRegisterViaInvite, db: AsyncSession = Depends(get_db)):
    """Register a doctor using an invitation code from a hospital admin.

    The invite_code links the doctor to the correct hospital.
    """
    # Find the invitation
    result = await db.execute(
        select(Invitation).where(
            Invitation.invite_code == data.invite_code,
            Invitation.status == InvitationStatus.PENDING,
        )
    )
    invitation = result.scalar_one_or_none()
    if not invitation:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Invalid or expired invitation code",
        )

    # Check invitation not expired
    if datetime.now(timezone.utc) > _ensure_aware(invitation.expires_at):
        invitation.status = InvitationStatus.EXPIRED
        await db.commit()
        raise HTTPException(
            status_code=status.HTTP_410_GONE,
            detail="Invitation has expired",
        )

    # Check phone matches invitation
    if data.phone != invitation.doctor_phone:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Phone number does not match the invitation",
        )

    # Check phone not already registered
    existing = await db.execute(select(Doctor).where(Doctor.phone == data.phone))
    if existing.scalar_one_or_none():
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Phone number already registered",
        )

    # Create doctor account
    doctor = Doctor(
        hospital_id=invitation.hospital_id,
        phone=data.phone,
        name=data.name,
        password_hash=hash_password(data.password),
        specialisation=data.specialisation or invitation.specialisation,
        license_number=data.license_number,
        email=data.email,
    )
    db.add(doctor)

    # Mark invitation as accepted
    invitation.status = InvitationStatus.ACCEPTED
    invitation.accepted_at = datetime.now(timezone.utc)

    await db.commit()
    await db.refresh(doctor)

    token = create_access_token(f"doctor:{doctor.id}")
    return DoctorTokenResponse(
        access_token=token,
        doctor=await _doctor_out_with_hospital(doctor, db),
    )


@router.post("/login", response_model=DoctorTokenResponse)
async def login_doctor(data: DoctorLogin, db: AsyncSession = Depends(get_db)):
    """Doctor login with phone + password."""
    result = await db.execute(select(Doctor).where(Doctor.phone == data.phone))
    doctor = result.scalar_one_or_none()
    if not doctor or not verify_password(data.password, doctor.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid phone or password",
        )

    token = create_access_token(f"doctor:{doctor.id}")
    return DoctorTokenResponse(
        access_token=token,
        doctor=await _doctor_out_with_hospital(doctor, db),
    )


@router.get("/me", response_model=DoctorOut)
async def get_doctor_profile(
    doctor: Doctor = Depends(get_current_doctor),
    db: AsyncSession = Depends(get_db),
):
    """Get the current doctor's profile (including hospital name)."""
    return await _doctor_out_with_hospital(doctor, db)


@router.get("/me/dashboard", response_model=DoctorDashboard)
async def get_doctor_dashboard(
    doctor: Doctor = Depends(get_current_doctor),
    db: AsyncSession = Depends(get_db),
):
    """Live dashboard data for the doctor home screen.

    Returns counts for today and this week, and the most recent briefings
    (patient_access_log rows joined to patient names).
    """
    now = datetime.now(timezone.utc)
    start_of_today = datetime(now.year, now.month, now.day, tzinfo=timezone.utc)
    start_of_week = start_of_today - timedelta(days=start_of_today.weekday())

    today_count_result = await db.execute(
        select(func.count())
        .select_from(PatientAccessLog)
        .where(
            PatientAccessLog.doctor_id == doctor.id,
            PatientAccessLog.accessed_at >= start_of_today,
        )
    )
    today_count = today_count_result.scalar_one() or 0

    week_count_result = await db.execute(
        select(func.count())
        .select_from(PatientAccessLog)
        .where(
            PatientAccessLog.doctor_id == doctor.id,
            PatientAccessLog.accessed_at >= start_of_week,
        )
    )
    week_count = week_count_result.scalar_one() or 0

    recent_result = await db.execute(
        select(PatientAccessLog, Patient.name)
        .join(Patient, Patient.id == PatientAccessLog.patient_id)
        .where(PatientAccessLog.doctor_id == doctor.id)
        .order_by(PatientAccessLog.accessed_at.desc())
        .limit(10)
    )
    recent_rows = recent_result.all()
    recent_briefings = [
        DoctorDashboardBriefing(
            id=log.id,
            patient_id=log.patient_id,
            patient_name=patient_name,
            method=log.method,
            accessed_at=log.accessed_at,
        )
        for log, patient_name in recent_rows
    ]

    return DoctorDashboard(
        today_count=today_count,
        week_count=week_count,
        avg_briefing_seconds=None,  # not tracked yet
        recent_briefings=recent_briefings,
    )
