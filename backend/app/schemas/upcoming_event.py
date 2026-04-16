"""Pydantic schemas for patient upcoming events (follow-ups surfaced from documents)."""

from datetime import date, datetime
from uuid import UUID

from pydantic import BaseModel


class UpcomingEventOut(BaseModel):
    id: UUID
    patient_id: UUID
    source_document_id: UUID | None = None

    kind: str
    title: str
    with_whom: str | None = None
    notes: str | None = None

    due_on: date | None = None
    due_hint_text: str | None = None
    urgency: str

    status: str
    completed_at: datetime | None = None
    dismissed_at: datetime | None = None

    created_at: datetime

    # Derived helpers — computed in the API layer so the mobile client doesn't
    # need to reason about timezones or stale data.
    is_overdue: bool = False
    days_until_due: int | None = None

    # "Looks done?" suggestion. When suggested_complete_by_document_id is
    # non-null the app shows a banner with tick/cross below the event.
    # suggested_complete_doc_label is a short human label built server-side
    # ("Lab report · 14 Apr") so the client doesn't have to re-fetch the doc
    # just to render the banner.
    suggested_complete_by_document_id: UUID | None = None
    suggested_complete_reason: str | None = None
    suggested_complete_doc_label: str | None = None

    model_config = {"from_attributes": True}


class UpcomingEventListOut(BaseModel):
    events: list[UpcomingEventOut]
    total: int
