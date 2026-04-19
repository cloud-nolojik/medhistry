"""Patient-level chat messages — scoped to a single patient across ALL their documents.

Unlike document_chat_message (which is per-document), these rows belong to the
patient as a whole. The AI has context of every uploaded report when replying.

Design notes:
- Keyed by (patient_id) — one thread per person (not per document).
- System prompt lives in code, never in the DB.
- Sliding history window (last N turns) assembled at request time.
"""

import uuid
from datetime import datetime, timezone

from sqlalchemy import String, DateTime, Text, ForeignKey, Index
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy.dialects.postgresql import UUID

from app.core.database import Base


class PatientChatMessage(Base):
    __tablename__ = "patient_chat_messages"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    patient_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("patients.id"), nullable=False, index=True
    )

    # 'user' or 'assistant'
    role: Mapped[str] = mapped_column(String(16), nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)

    # Tag if the AI short-circuited (emergency keyword, rate-limit, etc.)
    refusal_reason: Mapped[str | None] = mapped_column(String(64))

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        index=True,
    )

    __table_args__ = (
        Index("ix_patient_chat_patient_created", "patient_id", "created_at"),
    )
