"""Patient-level AI chat service.

The AI has context of EVERY uploaded document for the patient — not just one.
Grounding is built by compacting all documents' extracted_data + ai_summary
into a single context block ordered newest-first (most relevant first).

Architecture:
  - Sliding history window: last N turns from patient_chat_messages
  - Grounding: all completed docs for the patient, newest first, truncated at
    CONTEXT_MAX_CHARS to avoid token blowouts
  - Emergency keyword short-circuit (same as document chat)
  - Daily rate limit per patient
"""

from __future__ import annotations

import json
import logging
import re
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.models.medical_document import MedicalDocument
from app.models.patient_chat_message import PatientChatMessage
from app.services import gemini_service

logger = logging.getLogger(__name__)

# ─────────────────────────────────────────────────────────────────────────────
# Tunables
# ─────────────────────────────────────────────────────────────────────────────
HISTORY_WINDOW = 12          # turns sent to the model per request
DAILY_MESSAGE_QUOTA = 50     # user messages per patient per 24h
CONTEXT_MAX_CHARS = 12_000   # hard cap on total grounding text


# ─────────────────────────────────────────────────────────────────────────────
# System prompt
# ─────────────────────────────────────────────────────────────────────────────
SYSTEM_PROMPT = """You are MedHistry's personal health assistant. The patient has uploaded their medical
records to MedHistry and is talking to you directly from the app.

You have been given the full context of ALL their uploaded documents below — lab reports, prescriptions,
discharge summaries, and more. Use this context to give accurate, grounded, personalised answers.

Rules you MUST follow without exception:

1. Ground every factual claim in the PATIENT RECORDS provided. If the answer is not in the records,
   say so clearly: "I don't see that in your records — your doctor would know best."

2. You are NOT a doctor. Do not diagnose. Do not change or recommend dosages. Phrase findings neutrally:
   "Your latest report shows X. Your doctor can explain what that means for you."

3. For anything actionable (starting/stopping medicines, dose changes, diet restrictions during treatment,
   pregnancy, pain management, mental health), always add:
   "Please confirm with your doctor before making any changes."

4. Emergency short-circuit: if the user describes symptoms that could be life-threatening — chest pain,
   difficulty breathing, stroke signs, suicidal thoughts, severe bleeding — stop and respond with:
   "This sounds urgent. Please call your doctor now or go to the nearest emergency room.
   In India you can also call 112." Do NOT give home remedies.

5. Keep answers short and warm: 2–4 short paragraphs, plain everyday language, no jargon without
   explanation. Use the patient's own framing where possible.

6. You can compare results across documents — for example, track how HbA1c changed from one lab report
   to the next. Always reference the document date when discussing trends.

7. If asked about a document you don't have context for, say so.

8. Politely decline anything unrelated to the patient's health records (coding, legal, finance, etc.)
   with: "I'm here to help with your health records. I can't help with that."

Tone: warm, calm, reassuring but honest. Write at a 9th-grade reading level.
"""


# ─────────────────────────────────────────────────────────────────────────────
# Emergency detector (same keywords as document chat)
# ─────────────────────────────────────────────────────────────────────────────
_EMERGENCY_PATTERNS = [
    r"\bchest pain\b", r"\bcan('?t|not) breathe\b",
    r"\b(shortness of breath|breathless|gasping)\b",
    r"\bfainting\b", r"\bpassed out\b",
    r"\bblood (in (stool|vomit|urine)|vomit(ing)? blood)\b",
    r"\bseizure\b", r"\bstroke\b", r"\bunable to move\b",
    r"\bslurred speech\b", r"\bsevere headache\b",
    r"\bsuicid(e|al)\b", r"\bkill (myself|my self)\b",
    r"\bself[- ]harm\b", r"\boverdose\b",
]
_EMERGENCY_RE = re.compile("|".join(_EMERGENCY_PATTERNS), re.IGNORECASE)

EMERGENCY_REPLY = (
    "This sounds urgent. Please don't wait for me — call your doctor right now, "
    "or go to the nearest emergency room. In India you can also call **112** for "
    "emergency services or **iCall 9152987821** for mental-health support."
)


def detect_emergency(text: str) -> bool:
    return bool(_EMERGENCY_RE.search(text or ""))


# ─────────────────────────────────────────────────────────────────────────────
# Context builder — all documents for a patient, newest first
# ─────────────────────────────────────────────────────────────────────────────

def build_patient_context(documents: list[MedicalDocument]) -> str:
    """Compact all patient documents into a single grounding string.

    Ordered newest-first so the most recent info is at the top (models attend
    to early tokens more than late ones). Truncated at CONTEXT_MAX_CHARS.
    """
    # Sort newest first by document_date, fall back to created_at
    sorted_docs = sorted(
        documents,
        key=lambda d: (d.document_date or str(d.created_at))[:10],
        reverse=True,
    )

    parts: list[str] = [f"The patient has {len(sorted_docs)} uploaded document(s):\n"]

    for i, doc in enumerate(sorted_docs, 1):
        label = (doc.doc_type or "document").replace("_", " ").title()
        date_str = doc.document_date or str(doc.created_at)[:10]
        header = f"--- Document {i}: {label} | Date: {date_str}"
        if doc.hospital_name:
            header += f" | Hospital: {doc.hospital_name}"
        if doc.doctor_name:
            header += f" | Doctor: {doc.doctor_name}"
        header += " ---"

        body_parts = [header]
        if doc.ai_summary:
            body_parts.append(f"Summary: {doc.ai_summary}")
        if doc.extracted_data:
            try:
                # Include structured extraction compactly
                data_str = json.dumps(doc.extracted_data, ensure_ascii=False)
                body_parts.append(f"Extracted data: {data_str}")
            except (TypeError, ValueError):
                pass

        parts.append("\n".join(body_parts))

    full_context = "\n\n".join(parts)

    if len(full_context) > CONTEXT_MAX_CHARS:
        full_context = full_context[:CONTEXT_MAX_CHARS] + "\n\n…(older records truncated)"

    return full_context


# ─────────────────────────────────────────────────────────────────────────────
# Rate limiter
# ─────────────────────────────────────────────────────────────────────────────

async def count_user_messages_today(db: AsyncSession, patient_id) -> int:
    cutoff = datetime.now(timezone.utc) - timedelta(hours=24)
    result = await db.execute(
        select(func.count(PatientChatMessage.id)).where(
            PatientChatMessage.patient_id == patient_id,
            PatientChatMessage.role == "user",
            PatientChatMessage.created_at >= cutoff,
        )
    )
    return int(result.scalar() or 0)


# ─────────────────────────────────────────────────────────────────────────────
# History loader
# ─────────────────────────────────────────────────────────────────────────────

async def load_recent_history(
    db: AsyncSession, patient_id, limit: int = HISTORY_WINDOW
) -> list[dict]:
    result = await db.execute(
        select(PatientChatMessage)
        .where(PatientChatMessage.patient_id == patient_id)
        .order_by(PatientChatMessage.created_at.desc())
        .limit(limit)
    )
    rows = list(result.scalars().all())
    rows.reverse()  # oldest first for the model
    return [{"role": r.role, "content": r.content} for r in rows]


# ─────────────────────────────────────────────────────────────────────────────
# Main orchestrator
# ─────────────────────────────────────────────────────────────────────────────

@dataclass
class ChatReply:
    text: str
    refusal_reason: str | None  # 'red_flag' | 'rate_limit' | None


async def generate_reply(
    *,
    db: AsyncSession,
    patient_id,
    user_message: str,
    documents: list[MedicalDocument],
) -> ChatReply:
    """Generate a patient-level reply grounded in all their records.

    Does NOT persist messages — the API layer owns DB writes so it can
    handle rollback cleanly.
    """
    if detect_emergency(user_message):
        logger.info("Emergency keyword detected in patient chat for patient=%s", patient_id)
        return ChatReply(text=EMERGENCY_REPLY, refusal_reason="red_flag")

    grounding = build_patient_context(documents)
    history = await load_recent_history(db, patient_id)

    text = await gemini_service.chat_about_document(
        system_prompt=SYSTEM_PROMPT,
        grounding_context=grounding,
        history=history,
        user_message=user_message,
    )
    return ChatReply(text=text, refusal_reason=None)
