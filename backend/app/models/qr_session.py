"""QR Session model — tracks active QR sharing sessions between patient and doctor."""

import uuid
from datetime import datetime, timezone

from sqlalchemy import String, DateTime, Boolean, ForeignKey, Integer
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy.dialects.postgresql import UUID

from app.core.database import Base


class QRSession(Base):
    __tablename__ = "qr_sessions"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    patient_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("patients.id"), nullable=False, index=True
    )
    current_token: Mapped[str] = mapped_column(String(512), nullable=False)
    token_version: Mapped[int] = mapped_column(Integer, default=1)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    scanned_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    # Remote share code (optional): when the patient/son/daughter generates
    # a session for remote sharing over the phone, we also mint a short
    # 6-digit code. The doctor types it to redeem the same session without
    # needing a camera scan. NULL for normal QR sessions.
    share_code: Mapped[str | None] = mapped_column(String(16), index=True, unique=False)
    # "qr" (in-person QR flow) or "share_code" (remote phone-code flow).
    mode: Mapped[str] = mapped_column(String(20), default="qr")
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )
