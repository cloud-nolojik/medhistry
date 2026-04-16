"""PatientUpcomingEvent model — future actions extracted from uploaded documents.

Each row is one follow-up item surfaced to the patient on their home screen:
a repeat test, a specialist appointment, a vaccination booster, etc.

Lifecycle:
  pending → (user taps Done)       → completed
  pending → (user taps Dismiss)    → dismissed
  pending → (due_on < today - 14d) → overdue           (still actionable, nagged)
  Rows for DOCs older than STALE_DOC_THRESHOLD_DAYS are never inserted (see
  services/upcoming_events.py) — we don't want a 2-year-old discharge summary
  resurrecting expired follow-ups as if they're new tasks.
"""

import uuid
from datetime import datetime, timezone, date

from sqlalchemy import String, DateTime, Text, ForeignKey, Date, UniqueConstraint, Index
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy.dialects.postgresql import UUID

from app.core.database import Base


class PatientUpcomingEvent(Base):
    __tablename__ = "patient_upcoming_events"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )

    patient_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("patients.id"), nullable=False, index=True
    )

    # Which document surfaced this event. Nullable because we may re-point to a
    # newer document if the same follow-up is mentioned in a later upload.
    source_document_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("medical_documents.id", ondelete="CASCADE"),
        nullable=True,
        index=True,
    )

    # Core content
    kind: Mapped[str] = mapped_column(String(32), nullable=False)          # repeat_test, appointment, …
    title: Mapped[str] = mapped_column(String(200), nullable=False)
    with_whom: Mapped[str | None] = mapped_column(String(200))
    notes: Mapped[str | None] = mapped_column(Text)

    # Scheduling
    due_on: Mapped[date | None] = mapped_column(Date, nullable=True, index=True)
    due_hint_text: Mapped[str | None] = mapped_column(String(200))         # original doc phrase
    urgency: Mapped[str] = mapped_column(String(16), default="routine")    # routine | soon | urgent

    # Lifecycle
    status: Mapped[str] = mapped_column(String(16), default="pending", index=True)  # pending | completed | dismissed
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    dismissed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    # Completion suggestion: when a newly-uploaded document looks like it
    # fulfils this pending event (e.g. a lab report arrives containing the
    # very test this event was nagging about), we surface a "Looks done?"
    # banner in the app with tick/cross buttons. Tick → status=completed,
    # cross → dismiss just the suggestion (event stays pending). We never
    # auto-complete; the user always has to tap.
    # Null means no suggestion currently attached.
    suggested_complete_by_document_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("medical_documents.id", ondelete="CASCADE"),
        nullable=True,
    )
    suggested_complete_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    # Human-readable reason for the match ("HB, PCV found in lab report").
    # Powers the banner body so the user knows why we think it's done.
    suggested_complete_reason: Mapped[str | None] = mapped_column(String(200))

    # Dedup key: stable hash of (kind, normalized-title, approx-due-window).
    # Used so re-uploading the same discharge summary doesn't duplicate events.
    dedup_key: Mapped[str] = mapped_column(String(64), nullable=False)

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(timezone.utc),
        onupdate=lambda: datetime.now(timezone.utc),
    )

    __table_args__ = (
        UniqueConstraint("patient_id", "dedup_key", name="uq_patient_upcoming_dedup"),
        Index("ix_patient_upcoming_status_due", "patient_id", "status", "due_on"),
    )
