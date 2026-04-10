"""Patient model — core user entity for the patient-facing app."""

import uuid
from datetime import datetime, timezone

from sqlalchemy import String, DateTime, Text, Boolean, ForeignKey
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy.dialects.postgresql import UUID

from app.core.database import Base


class Patient(Base):
    __tablename__ = "patients"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    # Phone is unique only among primary account holders; dependents may share
    # the manager's phone or have no phone at all (elderly parents, kids).
    phone: Mapped[str | None] = mapped_column(String(15), index=True, nullable=True)
    name: Mapped[str] = mapped_column(String(100), nullable=False)
    date_of_birth: Mapped[str | None] = mapped_column(String(10))  # YYYY-MM-DD
    gender: Mapped[str | None] = mapped_column(String(20))
    blood_group: Mapped[str | None] = mapped_column(String(5))
    allergies: Mapped[str | None] = mapped_column(Text)
    medical_summary: Mapped[str | None] = mapped_column(Text)  # AI-generated summary
    # Dependents (Dad, Mom, kids) are full Patient rows managed by another
    # Patient (the primary account holder). NULL = self-managed primary.
    managed_by: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("patients.id"), nullable=True, index=True
    )
    # Relationship label shown in UI: "father", "mother", "son", "spouse", etc.
    relationship: Mapped[str | None] = mapped_column(String(30))
    # Dependents have no password/PIN (login impossible). Primary accounts must.
    password_hash: Mapped[str | None] = mapped_column(String(128), nullable=True)
    # 4-digit PIN (hashed) — used for quick login after OTP verification
    pin_hash: Mapped[str | None] = mapped_column(String(128), nullable=True)
    is_phone_verified: Mapped[bool] = mapped_column(Boolean, default=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        onupdate=lambda: datetime.now(timezone.utc),
    )
