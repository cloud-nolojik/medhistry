"""Invitation endpoints — hospital admin invites doctors."""

from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.models.hospital import Hospital
from app.models.invitation import Invitation, InvitationStatus
from app.schemas.invitation import InvitationCreate, InvitationOut, InvitationListOut
from app.api.deps import get_current_hospital

router = APIRouter(prefix="/invitations", tags=["invitations"])

INVITATION_EXPIRY_DAYS = 7


@router.post("/", response_model=InvitationOut, status_code=status.HTTP_201_CREATED)
async def create_invitation(
    data: InvitationCreate,
    hospital: Hospital = Depends(get_current_hospital),
    db: AsyncSession = Depends(get_db),
):
    """Hospital admin creates an invitation for a doctor.

    Returns the invite_code that the doctor uses to register.
    """
    # Check if there's already a pending invitation for this phone at this hospital
    existing = await db.execute(
        select(Invitation).where(
            Invitation.hospital_id == hospital.id,
            Invitation.doctor_phone == data.doctor_phone,
            Invitation.status == InvitationStatus.PENDING,
        )
    )
    if existing.scalar_one_or_none():
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Pending invitation already exists for this phone number",
        )

    invitation = Invitation(
        hospital_id=hospital.id,
        doctor_phone=data.doctor_phone,
        doctor_name=data.doctor_name,
        specialisation=data.specialisation,
        expires_at=datetime.now(timezone.utc) + timedelta(days=INVITATION_EXPIRY_DAYS),
    )
    db.add(invitation)
    await db.commit()
    await db.refresh(invitation)

    return InvitationOut.model_validate(invitation)


@router.get("/", response_model=InvitationListOut)
async def list_invitations(
    hospital: Hospital = Depends(get_current_hospital),
    db: AsyncSession = Depends(get_db),
):
    """List all invitations for this hospital."""
    result = await db.execute(
        select(Invitation)
        .where(Invitation.hospital_id == hospital.id)
        .order_by(Invitation.created_at.desc())
    )
    invitations = result.scalars().all()

    count_result = await db.execute(
        select(func.count()).select_from(Invitation).where(
            Invitation.hospital_id == hospital.id
        )
    )
    total = count_result.scalar()

    return InvitationListOut(
        invitations=[InvitationOut.model_validate(inv) for inv in invitations],
        total=total or 0,
    )


@router.delete("/{invitation_id}", status_code=status.HTTP_204_NO_CONTENT)
async def revoke_invitation(
    invitation_id: str,
    hospital: Hospital = Depends(get_current_hospital),
    db: AsyncSession = Depends(get_db),
):
    """Revoke a pending invitation."""
    from uuid import UUID
    result = await db.execute(
        select(Invitation).where(
            Invitation.id == UUID(invitation_id),
            Invitation.hospital_id == hospital.id,
            Invitation.status == InvitationStatus.PENDING,
        )
    )
    invitation = result.scalar_one_or_none()
    if not invitation:
        raise HTTPException(status_code=404, detail="Invitation not found or already used")

    invitation.status = InvitationStatus.REVOKED
    await db.commit()
