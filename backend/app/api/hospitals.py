"""Hospital admin endpoints — login and manage their own hospital.

Hospitals are created by MedHistry super admins, not self-registered.
Hospital admins log in with their slug + password to invite doctors.
"""

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import verify_password, create_access_token
from app.models.hospital import Hospital
from app.schemas.hospital import HospitalLogin, HospitalOut, HospitalTokenResponse
from app.api.deps import get_current_hospital

router = APIRouter(prefix="/hospitals", tags=["hospitals"])


@router.post("/login", response_model=HospitalTokenResponse)
async def login_hospital(data: HospitalLogin, db: AsyncSession = Depends(get_db)):
    """Hospital admin login with slug + password.

    The hospital account is created by MedHistry super admin.
    Hospital admin receives the slug and password from the MedHistry team.
    """
    result = await db.execute(select(Hospital).where(Hospital.slug == data.slug.lower()))
    hospital = result.scalar_one_or_none()
    if not hospital or not verify_password(data.password, hospital.admin_password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid slug or password",
        )

    token = create_access_token(f"hospital:{hospital.id}")
    return HospitalTokenResponse(
        access_token=token,
        hospital=HospitalOut.model_validate(hospital),
    )


@router.get("/me", response_model=HospitalOut)
async def get_hospital_profile(hospital: Hospital = Depends(get_current_hospital)):
    """Get the current hospital's profile."""
    return HospitalOut.model_validate(hospital)
