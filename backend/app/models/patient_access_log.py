"""Audit log of every successful doctor access to a patient's records.

Used to power the 'Recent access' list the primary account holder sees on
each family member's profile. Also satisfies DPDPA right-to-know-who-accessed.
"""

import uuid
from datetime import datetime, timezone

from sqlalchemy import String, DateTime, ForeignKey
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy.dialects.postgresql import UUID

from app.core.database import Base


class PatientAccessLog(Base):
    __tablename__ = "patient_access_logs"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    patient_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("patients.id"), nullable=False, index=True
    )
    doctor_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("doctors.id"), nullable=False, index=True
    )
    hospital_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("hospitals.id"), nullable=True
    )
    # "qr_scan" = in-person QR scan
    # "share_code" = remote 6-digit code redeemed by doctor
    method: Mapped[str] = mapped_column(String(20), nullable=False)
    accessed_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        index=True,
    )
