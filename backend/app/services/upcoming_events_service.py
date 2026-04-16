"""Patient upcoming-events service.

Takes the raw `follow_ups` array that Gemini/MedGemma extract from a document
and turns it into rows in the `patient_upcoming_events` table.

Responsibilities, in order:
    1. Parse absolute `due_on` strings (YYYY-MM-DD) into real date objects.
    2. When absent, anchor the `due_hint` phrase ("after 15 days", "in 3
       months", "2 weeks post-discharge") against the DOCUMENT date — not the
       upload date — so scanning an old prescription doesn't silently shift
       everything into the future.
    3. Drop follow-ups whose anchor document is older than
       STALE_DOC_THRESHOLD_DAYS (default: 180 days / ~6 months). Scanning a
       two-year-old discharge summary should not resurrect its "review in 3
       months" line as a fresh to-do; that window has closed. The document
       still appears in history and the doctor briefing, it just doesn't
       generate active events.
    4. Dedup against existing events for the same patient via a stable
       `dedup_key`: uploading the same PDF twice shouldn't create two copies.
    5. Upsert: re-pointing `source_document_id` to the newer doc when the same
       follow-up is mentioned in a fresher upload.

No network calls, no LLM calls — this is pure parsing on top of whatever the
extractor already returned. Keeps costs bounded and the logic unit-testable.
"""

from __future__ import annotations

import hashlib
import logging
import re
import uuid
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from typing import Iterable

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.patient_upcoming_event import PatientUpcomingEvent

logger = logging.getLogger(__name__)


# ──────────────────────────────────────────────────────────────────────────────
# Tunables
# ──────────────────────────────────────────────────────────────────────────────

# If the source document is older than this, we extract the follow-ups for
# display inside the doc, but we DO NOT insert them into the upcoming-events
# table. They're too stale to nag the user about.
STALE_DOC_THRESHOLD_DAYS = 180

# If a freshly-anchored due_on is this far in the past, we also skip — e.g. a
# recent doc that references a visit that's already well behind.
PAST_DUE_GRACE_DAYS = 30

VALID_KINDS = {
    "repeat_test", "appointment", "medication_review",
    "vaccination", "procedure", "lifestyle", "other",
}
VALID_URGENCY = {"routine", "soon", "urgent"}


# ──────────────────────────────────────────────────────────────────────────────
# Parsers
# ──────────────────────────────────────────────────────────────────────────────

def _parse_iso_date(s: str | None) -> date | None:
    if not s or not isinstance(s, str):
        return None
    s = s.strip()
    if not s or s.lower() == "null":
        return None
    # Gemini is usually strict but tolerate "YYYY-MM-DD" vs "YYYY/MM/DD"
    try:
        return date.fromisoformat(s.replace("/", "-"))
    except ValueError:
        return None


_REL_PATTERNS: list[tuple[re.Pattern[str], str]] = [
    # "after 15 days", "in 15 days", "15 days later", "15 days from now"
    (re.compile(r"(?:after|in|within)\s+(\d+)\s*day", re.I), "days"),
    (re.compile(r"(\d+)\s*day[s]?\s*(?:later|from\s+now|post|after)", re.I), "days"),
    # "in 3 weeks", "after 2 weeks", "2 weeks later", "2 weeks post-discharge"
    (re.compile(r"(?:after|in|within)\s+(\d+)\s*week", re.I), "weeks"),
    (re.compile(r"(\d+)\s*week[s]?\s*(?:later|from\s+now|post|after|post-discharge)", re.I), "weeks"),
    # "in 3 months", "after 6 months", "3 months later"
    (re.compile(r"(?:after|in|within)\s+(\d+)\s*month", re.I), "months"),
    (re.compile(r"(\d+)\s*month[s]?\s*(?:later|from\s+now|post|after)", re.I), "months"),
    # "in 1 year", "after 2 years", "1 year later"
    (re.compile(r"(?:after|in|within)\s+(\d+)\s*year", re.I), "years"),
    (re.compile(r"(\d+)\s*year[s]?\s*(?:later|from\s+now|post|after)", re.I), "years"),
]

_WORD_OFFSETS: dict[str, tuple[int, str]] = {
    "tomorrow": (1, "days"),
    "next week": (7, "days"),
    "next month": (1, "months"),
    "next year": (1, "years"),
    "fortnight": (14, "days"),
    "a week": (7, "days"),
    "a month": (1, "months"),
}


def _add_offset(anchor: date, n: int, unit: str) -> date:
    """Add an integer offset to an anchor date.

    Months/years use calendar-aware math (approximate: 30/365 days). For
    follow-up tracking a ±1 day drift is irrelevant; what matters is that a
    document dated 2026-01-10 saying "review in 3 months" produces ~2026-04-10,
    not shifted by the upload date.
    """
    if unit == "days":
        return anchor + timedelta(days=n)
    if unit == "weeks":
        return anchor + timedelta(weeks=n)
    if unit == "months":
        return anchor + timedelta(days=30 * n)
    if unit == "years":
        return anchor + timedelta(days=365 * n)
    return anchor


def resolve_due_on(
    explicit_due_on: str | None,
    due_hint: str | None,
    document_date: date | None,
) -> date | None:
    """Figure out the real calendar date for a follow-up item.

    Precedence:
      1. If the extractor gave us a usable YYYY-MM-DD, trust it.
      2. Else try to parse the due_hint phrase as a relative offset and anchor
         it against the document's own date.
      3. Else (e.g. "follow up if symptoms persist") return None — the event
         is still tracked, just without a calendar date.
    """
    parsed = _parse_iso_date(explicit_due_on)
    if parsed is not None:
        return parsed

    if not due_hint or not document_date:
        return None

    hint = due_hint.strip().lower()
    if not hint or hint == "null":
        return None

    # Word-level shortcuts first (don't need a number)
    for phrase, (n, unit) in _WORD_OFFSETS.items():
        if phrase in hint:
            return _add_offset(document_date, n, unit)

    for pattern, unit in _REL_PATTERNS:
        m = pattern.search(hint)
        if m:
            try:
                n = int(m.group(1))
            except (ValueError, IndexError):
                continue
            if 0 < n < 1000:  # sanity cap
                return _add_offset(document_date, n, unit)

    return None


# ──────────────────────────────────────────────────────────────────────────────
# Normalization + dedup
# ──────────────────────────────────────────────────────────────────────────────

@dataclass
class NormalizedFollowUp:
    kind: str
    title: str
    with_whom: str | None
    notes: str | None
    due_on: date | None
    due_hint_text: str | None
    urgency: str
    dedup_key: str


def _norm_title(title: str) -> str:
    # Collapse whitespace, lowercase, strip punctuation for dedup comparison.
    return re.sub(r"[^a-z0-9]+", " ", title.lower()).strip()


def _compute_dedup_key(kind: str, title: str, due_on: date | None) -> str:
    # Same kind + same title + same month bucket → same event. Without a date,
    # we fall back to just kind+title so repeat mentions without dates also
    # collapse.
    bucket = due_on.strftime("%Y-%m") if due_on else "no-date"
    raw = f"{kind}|{_norm_title(title)}|{bucket}"
    return hashlib.sha1(raw.encode()).hexdigest()[:32]


def _normalize_one(raw: dict, document_date: date | None) -> NormalizedFollowUp | None:
    if not isinstance(raw, dict):
        return None

    title = (raw.get("title") or "").strip()
    if not title:
        return None

    kind = (raw.get("kind") or "other").strip().lower()
    if kind not in VALID_KINDS:
        kind = "other"

    urgency = (raw.get("urgency") or "routine").strip().lower()
    if urgency not in VALID_URGENCY:
        urgency = "routine"

    due_on = resolve_due_on(
        raw.get("due_on"),
        raw.get("due_hint"),
        document_date,
    )

    due_hint_text = (raw.get("due_hint") or "").strip() or None
    with_whom = (raw.get("with_whom") or "").strip() or None
    notes = (raw.get("notes") or "").strip() or None

    return NormalizedFollowUp(
        kind=kind,
        title=title[:200],
        with_whom=with_whom[:200] if with_whom else None,
        notes=notes,
        due_on=due_on,
        due_hint_text=due_hint_text[:200] if due_hint_text else None,
        urgency=urgency,
        dedup_key=_compute_dedup_key(kind, title, due_on),
    )


# ──────────────────────────────────────────────────────────────────────────────
# Sync entry point
# ──────────────────────────────────────────────────────────────────────────────

def _today() -> date:
    return datetime.now(timezone.utc).date()


def is_document_too_stale(document_date: date | None, today: date | None = None) -> bool:
    """True if the source document is old enough that its follow-ups shouldn't
    generate active to-dos. Used so an old scanned prescription doesn't
    resurrect expired reminders."""
    if document_date is None:
        return False  # no date → trust the extractor; better to show than lose
    today = today or _today()
    return (today - document_date).days > STALE_DOC_THRESHOLD_DAYS


# ──────────────────────────────────────────────────────────────────────────────
# "Looks done?" fulfillment detection
#
# Runs after the events sync on every upload. Compares the new document's
# extracted data against the patient's existing pending events and, when the
# match is strong enough, attaches a suggestion to the event — the UI then
# renders a banner with tick/cross so the user can confirm or dismiss.
#
# Intentionally conservative: we only match kind="repeat_test" right now.
# Appointments are hard to verify from a document alone, and getting
# medication_review wrong risks telling the user their prescription is done
# when it's actually been re-prescribed. Scope will expand as we gather
# real-world signal.
# ──────────────────────────────────────────────────────────────────────────────


# Common medical test abbreviations. We don't need exhaustive coverage —
# just enough to catch the tests that patients see most often. Maps normalized
# abbreviation → set of normalized full-name variants (all lowercase, no
# punctuation) the lab report might use.
_TEST_ALIASES: dict[str, set[str]] = {
    "hb": {"hb", "hemoglobin", "haemoglobin"},
    "hgb": {"hgb", "hemoglobin", "haemoglobin"},
    "pcv": {"pcv", "packed cell volume", "hematocrit", "haematocrit", "hct"},
    "hct": {"hct", "hematocrit", "haematocrit", "pcv"},
    "tlc": {"tlc", "total leukocyte count", "total leucocyte count", "wbc", "white blood cell count"},
    "wbc": {"wbc", "white blood cell count", "total leukocyte count", "tlc"},
    "rbc": {"rbc", "red blood cell count"},
    "plt": {"plt", "platelet count", "platelets"},
    "esr": {"esr", "erythrocyte sedimentation rate"},
    "crp": {"crp", "c reactive protein", "c-reactive protein"},
    "fbs": {"fbs", "fasting blood sugar", "fasting glucose", "glucose fasting"},
    "ppbs": {"ppbs", "post prandial blood sugar", "postprandial glucose"},
    "rbs": {"rbs", "random blood sugar", "random glucose"},
    "hba1c": {"hba1c", "glycated hemoglobin", "glycosylated hemoglobin"},
    "tsh": {"tsh", "thyroid stimulating hormone"},
    "t3": {"t3", "triiodothyronine"},
    "t4": {"t4", "thyroxine"},
    "ldl": {"ldl", "ldl cholesterol", "low density lipoprotein"},
    "hdl": {"hdl", "hdl cholesterol", "high density lipoprotein"},
    "vldl": {"vldl", "vldl cholesterol"},
    "tg": {"tg", "triglycerides"},
    "cbc": {"cbc", "complete blood count"},
    "lft": {"lft", "liver function test"},
    "kft": {"kft", "kidney function test", "rft", "renal function test"},
    "bun": {"bun", "blood urea nitrogen", "urea"},
    "cr": {"cr", "creatinine", "serum creatinine"},
    "vit d": {"vit d", "vitamin d", "25 hydroxyvitamin d"},
    "b12": {"b12", "vitamin b12", "cobalamin"},
}


def _normalize_test_token(s: str) -> str:
    """Lowercase, strip punctuation, collapse whitespace."""
    return re.sub(r"[^a-z0-9]+", " ", s.lower()).strip()


def _expand_aliases(token: str) -> set[str]:
    """Given a normalized token, return itself plus any known aliases."""
    expanded = {token}
    if token in _TEST_ALIASES:
        expanded |= _TEST_ALIASES[token]
    # Reverse lookup — if the token IS a full name, include its abbreviation(s).
    for abbr, variants in _TEST_ALIASES.items():
        if token in variants:
            expanded.add(abbr)
            expanded |= variants
    return expanded


def _extract_test_tokens_from_title(title: str) -> set[str]:
    """Pull out test names/abbreviations from an event title.

    Handles the common "Repeat blood tests (HB, PCV)" pattern as well as
    simpler titles like "Repeat HbA1c" or "Follow-up TSH". Returns normalized
    tokens with their aliases expanded so we can match against any variant
    spelling the lab report uses.
    """
    tokens: set[str] = set()
    # Extract anything inside parens first — that's where the test list lives
    # in the "Repeat blood tests (HB, PCV)" pattern.
    for paren in re.findall(r"\(([^)]+)\)", title):
        for piece in re.split(r"[,/&+]| and ", paren, flags=re.I):
            norm = _normalize_test_token(piece)
            if norm and len(norm) <= 40:
                tokens |= _expand_aliases(norm)
    # Also consider bare-word tokens in the rest of the title that look like
    # medical abbreviations (2-6 alphanumerics, all caps or known alias).
    stripped = re.sub(r"\([^)]*\)", " ", title)
    for word in re.findall(r"[A-Za-z0-9]+", stripped):
        lower = word.lower()
        if lower in _TEST_ALIASES or (2 <= len(word) <= 6 and word.isupper()):
            tokens |= _expand_aliases(lower)
    return tokens


def _collect_doc_test_tokens(extracted: dict | None) -> set[str]:
    """Gather normalized test-name tokens (with aliases) from a document.

    Looks at lab_results[*].test_name and vitals[*].name — those are the two
    arrays the extractor uses for anything measurable.
    """
    if not extracted:
        return set()
    tokens: set[str] = set()
    for row in (extracted.get("lab_results") or []):
        if isinstance(row, dict):
            name = row.get("test_name") or ""
            norm = _normalize_test_token(name)
            if norm:
                tokens |= _expand_aliases(norm)
    for row in (extracted.get("vitals") or []):
        if isinstance(row, dict):
            name = row.get("name") or ""
            norm = _normalize_test_token(name)
            if norm:
                tokens |= _expand_aliases(norm)
    return tokens


def _build_reason(matched: list[str]) -> str:
    """Short, patient-friendly explanation of why we think this is done."""
    display = ", ".join(sorted({m.upper() if len(m) <= 6 else m.title() for m in matched[:3]}))
    if len(matched) > 3:
        display += f" +{len(matched) - 3} more"
    return f"{display} found in this report"


async def detect_fulfillments_for_document(
    *,
    db: AsyncSession,
    patient_id: uuid.UUID,
    document_id: uuid.UUID,
    extracted_data: dict | None,
) -> list[PatientUpcomingEvent]:
    """Attach "Looks done?" suggestions to pending events this doc may fulfil.

    Called from the document pipeline AFTER sync_upcoming_events_for_document
    has run, so newly-created events from the same doc aren't accidentally
    flagged as "done by themselves".

    Runs inside the caller's transaction — we only set fields, we never
    commit.
    """
    if not extracted_data:
        return []

    doc_tokens = _collect_doc_test_tokens(extracted_data)
    if not doc_tokens:
        return []

    # Pull pending events for this patient. Skip events that already have a
    # different suggestion attached, or that were created from THIS same
    # document (those are fresh, not fulfilled).
    q = await db.execute(
        select(PatientUpcomingEvent).where(
            PatientUpcomingEvent.patient_id == patient_id,
            PatientUpcomingEvent.status == "pending",
            PatientUpcomingEvent.kind == "repeat_test",
            PatientUpcomingEvent.source_document_id != document_id,
        )
    )
    pending_events = list(q.scalars().all())
    if not pending_events:
        return []

    now = datetime.now(timezone.utc)
    touched: list[PatientUpcomingEvent] = []

    for ev in pending_events:
        event_tokens = _extract_test_tokens_from_title(ev.title)
        if not event_tokens:
            continue
        overlap = event_tokens & doc_tokens
        if not overlap:
            continue
        # Prefer shorter tokens (abbreviations) for the reason so the label
        # stays terse — "HB, PCV found" reads better than "hemoglobin,
        # packed cell volume found".
        matched = sorted(overlap, key=len)
        ev.suggested_complete_by_document_id = document_id
        ev.suggested_complete_reason = _build_reason(matched)
        ev.suggested_complete_at = now
        touched.append(ev)
        logger.info(
            "Attached completion suggestion to event %s via document %s (matched: %s)",
            ev.id, document_id, matched,
        )

    return touched


async def sync_upcoming_events_for_document(
    *,
    db: AsyncSession,
    patient_id: uuid.UUID,
    document_id: uuid.UUID,
    document_date: date | None,
    follow_ups: Iterable[dict] | None,
) -> list[PatientUpcomingEvent]:
    """Merge the follow-ups from a single document into the patient's event list.

    Returns the list of events that were created or updated (for logging /
    testing). Callers should await this INSIDE the same transaction as the
    document update so a failure here rolls back the whole document write.
    """
    if not follow_ups:
        return []

    if is_document_too_stale(document_date):
        logger.info(
            "Skipping upcoming-event sync for document %s: document_date=%s is "
            "older than %sd threshold (patient %s)",
            document_id, document_date, STALE_DOC_THRESHOLD_DAYS, patient_id,
        )
        return []

    today = _today()
    touched: list[PatientUpcomingEvent] = []

    for raw in follow_ups:
        norm = _normalize_one(raw, document_date)
        if norm is None:
            continue

        # Drop events that are already well in the past — the doc is recent but
        # it references a visit that's already behind us.
        if norm.due_on is not None and (today - norm.due_on).days > PAST_DUE_GRACE_DAYS:
            logger.info(
                "Skipping upcoming-event %r: due_on=%s is >%sd in the past",
                norm.title, norm.due_on, PAST_DUE_GRACE_DAYS,
            )
            continue

        # Upsert on (patient_id, dedup_key).
        existing_q = await db.execute(
            select(PatientUpcomingEvent).where(
                PatientUpcomingEvent.patient_id == patient_id,
                PatientUpcomingEvent.dedup_key == norm.dedup_key,
            )
        )
        existing = existing_q.scalar_one_or_none()

        if existing is None:
            event = PatientUpcomingEvent(
                patient_id=patient_id,
                source_document_id=document_id,
                kind=norm.kind,
                title=norm.title,
                with_whom=norm.with_whom,
                notes=norm.notes,
                due_on=norm.due_on,
                due_hint_text=norm.due_hint_text,
                urgency=norm.urgency,
                status="pending",
                dedup_key=norm.dedup_key,
            )
            db.add(event)
            touched.append(event)
        else:
            # Only refresh if the user hasn't already acted on it.
            if existing.status == "pending":
                existing.source_document_id = document_id
                existing.title = norm.title
                existing.with_whom = norm.with_whom
                existing.notes = norm.notes
                existing.due_on = norm.due_on
                existing.due_hint_text = norm.due_hint_text
                existing.urgency = norm.urgency
                touched.append(existing)

    # Let the outer transaction flush. We do not commit here — the document
    # pipeline owns the transaction boundary.
    return touched
