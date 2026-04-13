"""Invitation model — hospital admin invites a doctor to join."""

import uuid
import secrets
import string
from datetime import datetime, timezone


def _generate_invite_code() -> str:
    """Generate an 8-character uppercase alphanumeric invite code."""
    chars = string.ascii_uppercase + string.digits
    return ''.join(secrets.choice(chars) for _ in range(8))

from sqlalchemy import String, DateTime, Boolean, ForeignKey, Enum as SAEnum
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy.dialects.postgresql import UUID

from app.core.database import Base

import enum


class InvitationStatus(str, enum.Enum):
    PENDING = "pending"
    ACCEPTED = "accepted"
    EXPIRED = "expired"
    REVOKED = "revoked"


class Invitation(Base):
    __tablename__ = "invitations"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    hospital_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("hospitals.id"), nullable=False, index=True
    )
    doctor_phone: Mapped[str] = mapped_column(String(15), nullable=False)
    doctor_name: Mapped[str] = mapped_column(String(100), nullable=False)
    doctor_email: Mapped[str | None] = mapped_column(String(200))
    specialisation: Mapped[str | None] = mapped_column(String(100))
    invite_code: Mapped[str] = mapped_column(
        String(32), unique=True, index=True, nullable=False,
        default=_generate_invite_code
    )
    status: Mapped[InvitationStatus] = mapped_column(
        SAEnum(InvitationStatus, native_enum=False, length=20),
        default=InvitationStatus.PENDING,
    )
    accepted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )
