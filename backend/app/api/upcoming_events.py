"""Patient upcoming-events API.

Surfaces the follow-ups that were extracted from uploaded documents as a
separate, actionable list. Patients can see what's coming, mark items done,
dismiss them, and export any single item to their phone calendar (ICS).

All endpoints are scoped to the authenticated primary patient; dependents are
addressed via ?patient_id= and validated through resolve_patient_context, so
the same JWT covers self + family members.
"""

from __future__ import annotations

from datetime import date, datetime, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from fastapi.responses import Response
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.api.deps import get_current_patient, resolve_patient_context
from app.models.patient import Patient
from app.models.patient_upcoming_event import PatientUpcomingEvent
from app.models.medical_document import MedicalDocument
from app.schemas.upcoming_event import UpcomingEventOut, UpcomingEventListOut


router = APIRouter(prefix="/upcoming-events", tags=["upcoming-events"])


OVERDUE_GRACE_DAYS = 0  # due_on strictly before today → overdue


_DOC_TYPE_LABELS = {
    "prescription": "Prescription",
    "lab_report": "Lab report",
    "discharge_summary": "Discharge summary",
    "imaging_report": "Imaging report",
}


def _short_month(d: date) -> str:
    return d.strftime("%-d %b") if hasattr(d, "strftime") else str(d)


def _build_doc_label(doc: MedicalDocument | None) -> str | None:
    """Compact label like 'Lab report · 14 Apr' for the suggestion banner."""
    if doc is None:
        return None
    doc_type_label = _DOC_TYPE_LABELS.get(doc.doc_type or "", "Document")
    # Prefer the date printed on the actual medical document; fall back to
    # upload timestamp if extraction didn't pick one out.
    when: date | None = None
    if doc.document_date:
        # document_date is stored as a "YYYY-MM-DD" string
        try:
            when = date.fromisoformat(doc.document_date[:10])
        except (ValueError, TypeError):
            when = None
    if when is None and doc.created_at is not None:
        when = doc.created_at.date()
    if when is None:
        return doc_type_label
    try:
        label_date = when.strftime("%-d %b")
    except ValueError:
        # Windows Python doesn't support %-d; fall back to a portable format.
        label_date = when.strftime("%d %b").lstrip("0")
    return f"{doc_type_label} · {label_date}"


def _serialize(
    ev: PatientUpcomingEvent,
    today: date,
    doc_label: str | None = None,
) -> UpcomingEventOut:
    days_until_due: int | None = None
    is_overdue = False
    if ev.due_on is not None:
        delta = (ev.due_on - today).days
        days_until_due = delta
        is_overdue = ev.status == "pending" and delta < -OVERDUE_GRACE_DAYS
    return UpcomingEventOut(
        id=ev.id,
        patient_id=ev.patient_id,
        source_document_id=ev.source_document_id,
        kind=ev.kind,
        title=ev.title,
        with_whom=ev.with_whom,
        notes=ev.notes,
        due_on=ev.due_on,
        due_hint_text=ev.due_hint_text,
        urgency=ev.urgency,
        status=ev.status,
        completed_at=ev.completed_at,
        dismissed_at=ev.dismissed_at,
        created_at=ev.created_at,
        is_overdue=is_overdue,
        days_until_due=days_until_due,
        suggested_complete_by_document_id=ev.suggested_complete_by_document_id,
        suggested_complete_reason=ev.suggested_complete_reason,
        suggested_complete_doc_label=doc_label,
    )


async def _load_event(
    event_id: UUID,
    primary: Patient,
    db: AsyncSession,
) -> PatientUpcomingEvent:
    """Load an event and enforce that it belongs to a patient this account manages."""
    q = await db.execute(
        select(PatientUpcomingEvent).where(PatientUpcomingEvent.id == event_id)
    )
    ev = q.scalar_one_or_none()
    if ev is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Event not found")
    # Authorize via resolve_patient_context — raises 403 if the primary cannot
    # act on this patient (not self, not a managed dependent).
    await resolve_patient_context(ev.patient_id, primary, db)
    return ev


@router.get("", response_model=UpcomingEventListOut)
async def list_events(
    patient_id: UUID | None = Query(
        None,
        description="Target patient (dependent) — defaults to self.",
    ),
    include_completed: bool = Query(False, description="Include completed/dismissed events."),
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """List upcoming events for the patient (self or a managed dependent).

    Ordering rule: urgency first (urgent → soon → routine), then soonest due
    date, then created_at. Events without a date sort to the bottom of their
    urgency bucket.
    """
    target = await resolve_patient_context(patient_id or primary.id, primary, db)

    stmt = select(PatientUpcomingEvent).where(
        PatientUpcomingEvent.patient_id == target.id
    )
    if not include_completed:
        stmt = stmt.where(PatientUpcomingEvent.status == "pending")

    result = await db.execute(stmt)
    rows = list(result.scalars().all())

    # Python-side sort so the three-level ordering is portable across Postgres/SQLite.
    urgency_rank = {"urgent": 0, "soon": 1, "routine": 2}
    rows.sort(key=lambda e: (
        urgency_rank.get(e.urgency, 2),
        e.due_on or date.max,
        e.created_at,
    ))

    # Batch-load any suggesting documents so we can render the banner label
    # ("Lab report · 14 Apr") without N+1 queries. Only rows with an active
    # completion suggestion trigger a lookup.
    suggested_doc_ids = {
        e.suggested_complete_by_document_id for e in rows
        if e.suggested_complete_by_document_id is not None
    }
    doc_label_by_id: dict[UUID, str | None] = {}
    if suggested_doc_ids:
        doc_q = await db.execute(
            select(MedicalDocument).where(MedicalDocument.id.in_(suggested_doc_ids))
        )
        for d in doc_q.scalars().all():
            doc_label_by_id[d.id] = _build_doc_label(d)

    today = datetime.now(timezone.utc).date()
    return UpcomingEventListOut(
        events=[
            _serialize(e, today, doc_label_by_id.get(e.suggested_complete_by_document_id))
            for e in rows
        ],
        total=len(rows),
    )


def _clear_suggestion(ev: PatientUpcomingEvent) -> None:
    ev.suggested_complete_by_document_id = None
    ev.suggested_complete_reason = None
    ev.suggested_complete_at = None


@router.post("/{event_id}/complete", response_model=UpcomingEventOut)
async def complete_event(
    event_id: UUID,
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Mark an event as done — the patient has completed the action."""
    ev = await _load_event(event_id, primary, db)
    if ev.status == "completed":
        # Idempotent — returning 200 instead of 409 simplifies mobile retries.
        today = datetime.now(timezone.utc).date()
        return _serialize(ev, today)
    ev.status = "completed"
    ev.completed_at = datetime.now(timezone.utc)
    # Any pending completion suggestion is now moot.
    _clear_suggestion(ev)
    await db.commit()
    await db.refresh(ev)
    today = datetime.now(timezone.utc).date()
    return _serialize(ev, today)


@router.post("/{event_id}/dismiss", response_model=UpcomingEventOut)
async def dismiss_event(
    event_id: UUID,
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Dismiss an event — patient has decided it's not applicable (e.g. extracted in error)."""
    ev = await _load_event(event_id, primary, db)
    if ev.status == "dismissed":
        today = datetime.now(timezone.utc).date()
        return _serialize(ev, today)
    ev.status = "dismissed"
    ev.dismissed_at = datetime.now(timezone.utc)
    _clear_suggestion(ev)
    await db.commit()
    await db.refresh(ev)
    today = datetime.now(timezone.utc).date()
    return _serialize(ev, today)


@router.post("/{event_id}/dismiss-suggestion", response_model=UpcomingEventOut)
async def dismiss_suggestion(
    event_id: UUID,
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Clear the "Looks done?" suggestion without changing the event's status.

    The patient tapped the cross on the suggestion banner: the event stays
    pending, but we stop nudging them about this particular match. If the
    same document matches again on a future re-extract we'd surface the
    suggestion again — that's intentional; the signal is cheap to rebuild
    and the user has already trained us once per event.
    """
    ev = await _load_event(event_id, primary, db)
    _clear_suggestion(ev)
    await db.commit()
    await db.refresh(ev)
    today = datetime.now(timezone.utc).date()
    return _serialize(ev, today)


# ──────────────────────────────────────────────────────────────────────────────
# Calendar export (ICS) — works with every major calendar app without needing
# any third-party integration or OAuth.
# ──────────────────────────────────────────────────────────────────────────────

def _ics_escape(text: str) -> str:
    return (
        text.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
    )


def _build_ics(ev: PatientUpcomingEvent) -> str:
    # Use an all-day event if we have a date; fall back to today if not — the
    # mobile client should only show "Add to calendar" when due_on is present,
    # so the fallback is a safety net.
    dt = ev.due_on or datetime.now(timezone.utc).date()
    dtstart = dt.strftime("%Y%m%d")
    dtend = (dt.replace(day=dt.day)).strftime("%Y%m%d")  # same day (all-day = dtstart+1)
    # For all-day events DTEND should be the next day per RFC 5545.
    from datetime import timedelta
    dtend = (dt + timedelta(days=1)).strftime("%Y%m%d")

    summary = _ics_escape(ev.title)
    description_bits = []
    if ev.with_whom:
        description_bits.append(f"With: {ev.with_whom}")
    if ev.notes:
        description_bits.append(ev.notes)
    if ev.due_hint_text:
        description_bits.append(f"From document: {ev.due_hint_text}")
    description = _ics_escape("\n".join(description_bits) or "MedHistry reminder")

    uid = f"{ev.id}@medhistry"
    dtstamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")

    return (
        "BEGIN:VCALENDAR\r\n"
        "VERSION:2.0\r\n"
        "PRODID:-//MedHistry//Upcoming Events//EN\r\n"
        "CALSCALE:GREGORIAN\r\n"
        "METHOD:PUBLISH\r\n"
        "BEGIN:VEVENT\r\n"
        f"UID:{uid}\r\n"
        f"DTSTAMP:{dtstamp}\r\n"
        f"DTSTART;VALUE=DATE:{dtstart}\r\n"
        f"DTEND;VALUE=DATE:{dtend}\r\n"
        f"SUMMARY:{summary}\r\n"
        f"DESCRIPTION:{description}\r\n"
        "BEGIN:VALARM\r\n"
        "TRIGGER:-P1D\r\n"
        "ACTION:DISPLAY\r\n"
        f"DESCRIPTION:{summary}\r\n"
        "END:VALARM\r\n"
        "END:VEVENT\r\n"
        "END:VCALENDAR\r\n"
    )


@router.get("/{event_id}/calendar.ics")
async def calendar_ics(
    event_id: UUID,
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Return an RFC 5545 ICS file for a single event so the mobile OS can
    add it to the user's native calendar (Google Calendar, Apple Calendar,
    Outlook, etc). A 1-day-before alarm is included by default."""
    ev = await _load_event(event_id, primary, db)
    body = _build_ics(ev)
    filename = f"medhistry-event-{ev.id}.ics"
    return Response(
        content=body,
        media_type="text/calendar; charset=utf-8",
        headers={
            "Content-Disposition": f'attachment; filename="{filename}"',
            "Cache-Control": "no-store",
        },
    )
