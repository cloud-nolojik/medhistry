"""Per-document chat messages.

A single conversation is scoped to (patient_id, document_id): patients can ask
questions about one specific report / prescription at a time. Cross-document
chat is deliberately NOT in v1 — we want to learn what people actually ask
before investing in a retrieval layer across all documents.

Each row is one message. `role` is 'user' or 'assistant'. System messages are
never persisted — they live in the service layer so we can evolve the safety
prompt without rewriting history.
"""

import uuid
from datetime import datetime, timezone

from sqlalchemy import String, DateTime, Text, ForeignKey, Index
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy.dialects.postgresql import UUID

from app.core.database import Base


class DocumentChatMessage(Base):
    __tablename__ = "document_chat_messages"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )

    patient_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("patients.id"), nullable=False, index=True
    )
    document_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("medical_documents.id"), nullable=False, index=True
    )

    # Who wrote this turn. We only persist 'user' and 'assistant' — the system
    # prompt with safety rules lives in code and is reapplied every call so we
    # can upgrade it without touching the stored history.
    role: Mapped[str] = mapped_column(String(16), nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False)

    # If the assistant refused or escalated (red-flag keyword match, rate-limit
    # hit, out-of-scope request), we tag the reason so product can audit later.
    # Null for normal turns.
    refusal_reason: Mapped[str | None] = mapped_column(String(64))

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc), index=True
    )

    __table_args__ = (
        Index("ix_doc_chat_patient_doc_created", "patient_id", "document_id", "created_at"),
    )
