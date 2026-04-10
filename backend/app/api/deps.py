"""Shared API dependencies: auth for patients, doctors, hospital admins, and super admins."""

from uuid import UUID

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import decode_access_token
from app.models.patient import Patient
from app.models.doctor import Doctor
from app.models.hospital import Hospital
from app.models.super_admin import SuperAdmin

security = HTTPBearer()


async def get_current_super_admin(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    db: AsyncSession = Depends(get_db),
) -> SuperAdmin:
    """Extract and validate super admin from JWT (subject = 'superadmin:<uuid>')."""
    subject = decode_access_token(credentials.credentials)
    if not subject or not subject.startswith("superadmin:"):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid or expired super admin token")

    admin_id = subject.split(":", 1)[1]
    result = await db.execute(select(SuperAdmin).where(SuperAdmin.id == UUID(admin_id)))
    admin = result.scalar_one_or_none()
    if not admin or not admin.is_active:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Super admin not found or inactive")
    return admin


async def get_current_patient(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    db: AsyncSession = Depends(get_db),
) -> Patient:
    """Extract and validate patient from JWT bearer token."""
    subject = decode_access_token(credentials.credentials)
    if not subject:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid or expired token")

    # Patient tokens have plain UUID as subject
    if ":" in subject:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Not a patient token")

    result = await db.execute(select(Patient).where(Patient.id == UUID(subject)))
    patient = result.scalar_one_or_none()
    if not patient or not patient.is_active:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Patient not found or inactive")
    # Only primary account holders (self-managed) can authenticate. Dependents
    # have no password and no login path.
    if patient.managed_by is not None:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Dependent profiles cannot authenticate directly",
        )
    return patient


async def resolve_patient_context(
    target_patient_id: UUID,
    primary: Patient,
    db: AsyncSession,
) -> Patient:
    """Resolve the Patient the primary account is acting on.

    The primary can act on:
    - Themselves (target_patient_id == primary.id)
    - Any dependent whose managed_by == primary.id

    Raises 403 otherwise. Used by every endpoint that takes a patient_id in
    the URL/body so the same JWT can manage Dad, Mom, and self.
    """
    if target_patient_id == primary.id:
        return primary
    result = await db.execute(
        select(Patient).where(
            Patient.id == target_patient_id,
            Patient.managed_by == primary.id,
            Patient.is_active == True,
        )
    )
    dependent = result.scalar_one_or_none()
    if not dependent:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Not authorized to act on this patient",
        )
    return dependent


async def get_current_doctor(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    db: AsyncSession = Depends(get_db),
) -> Doctor:
    """Extract and validate doctor from JWT bearer token (subject = 'doctor:<uuid>')."""
    subject = decode_access_token(credentials.credentials)
    if not subject or not subject.startswith("doctor:"):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid or expired doctor token")

    doctor_id = subject.split(":", 1)[1]
    result = await db.execute(select(Doctor).where(Doctor.id == UUID(doctor_id)))
    doctor = result.scalar_one_or_none()
    if not doctor or not doctor.is_active:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Doctor not found or inactive")
    return doctor


async def get_current_hospital(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    db: AsyncSession = Depends(get_db),
) -> Hospital:
    """Extract and validate hospital admin from JWT bearer token (subject = 'hospital:<uuid>')."""
    subject = decode_access_token(credentials.credentials)
    if not subject or not subject.startswith("hospital:"):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid or expired hospital token")

    hospital_id = subject.split(":", 1)[1]
    result = await db.execute(select(Hospital).where(Hospital.id == UUID(hospital_id)))
    hospital = result.scalar_one_or_none()
    if not hospital or not hospital.is_active:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Hospital not found or inactive")
    return hospital
