"""Per-document chat service.

Everything that wraps the raw Gemini call:
  - System prompt with medical-safety guardrails (no diagnosis, no dosing,
    always defer to the doctor for actionable decisions)
  - Red-flag keyword detector that short-circuits to a crisis/ER response
    BEFORE hitting the model, so a bad LLM moment can't make things worse
  - Per-patient daily rate limiter (keeps cost bounded and defuses abuse)
  - Grounded context builder from extracted_data + ai_summary
  - Short sliding history window (last N turns) so we don't replay a 50-turn
    conversation on every send

The actual DB I/O happens in the API layer. This module is pure logic + one
outbound call to `gemini_service.chat_about_document`.
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
from app.models.document_chat_message import DocumentChatMessage
from app.models.medical_document import MedicalDocument
from app.services import gemini_service

logger = logging.getLogger(__name__)


# ──────────────────────────────────────────────────────────────────────────────
# Tunables
# ──────────────────────────────────────────────────────────────────────────────

HISTORY_WINDOW_TURNS = 10                 # last N messages sent to the model
DAILY_USER_MESSAGE_QUOTA = 30             # per-patient, per doc
CONTEXT_MAX_CHARS = 6000                  # trim grounding so tokens stay bounded


# ──────────────────────────────────────────────────────────────────────────────
# System prompt — the one piece that is NEVER user-controlled.
# ──────────────────────────────────────────────────────────────────────────────

SYSTEM_PROMPT = """You are MedHistry's patient-facing medical assistant. The user has
uploaded a medical document (prescription, lab report, discharge summary, etc.)
and wants to understand it better.

Rules you MUST follow, without exception:

1. Ground every factual claim about the patient in the DOCUMENT CONTEXT below.
   If the answer is not supported by the context, say so plainly: "I don't see
   that in this report — please check with your doctor." Never invent values.

2. You are NOT a doctor. Do not diagnose. Do not adjust dosages. Do not say
   "you have X disease." Phrase findings neutrally, e.g. "Your report shows
   X. Your doctor can explain what this means for you."

3. For anything actionable (starting/stopping medicine, changing dose, food or
   alcohol interactions, pregnancy, pain management, mental-health treatment),
   include a clear line like: "Please confirm with your doctor before
   changing anything."

4. If the user describes symptoms that could be an emergency — chest pain,
   trouble breathing, fainting, severe bleeding, suicidal thoughts, signs of
   stroke — stop the medical explanation and respond with: "This sounds
   urgent. Please call your doctor right now, or go to the nearest emergency
   room. In India you can also call 112." Do not give home remedies in these
   cases.

5. Keep answers short: 2–4 short paragraphs, plain language, no medical
   jargon without explanation. Use the patient's own framing.

6. Never read test values from memory; use only the specific numbers in the
   DOCUMENT CONTEXT. If a value is missing, say it is not in the document.

7. Decline politely if the user asks something unrelated to this medical
   document (coding help, legal advice, other people's reports, etc.) with:
   "I can only help with questions about this specific report."

Tone: warm, calm, reassuring but honest. Write at a 9th-grade reading level.
"""


# ──────────────────────────────────────────────────────────────────────────────
# Red-flag detector (runs BEFORE the LLM call)
#
# This is intentionally simple and client-side-style. The goal is not to
# be medically exhaustive — it's to catch the obvious emergencies so we can
# fail safely if the model is having a bad day. Gemini's own safety layer is
# the secondary defense.
# ──────────────────────────────────────────────────────────────────────────────

_EMERGENCY_PATTERNS = [
    r"\bchest pain\b",
    r"\bcan('?t|not) breathe\b",
    r"\b(shortness of breath|breathless|gasping)\b",
    r"\bfainting\b",
    r"\bpassed out\b",
    r"\bblood (in (stool|vomit|urine)|vomit(ing)? blood)\b",
    r"\bseizure\b",
    r"\bstroke\b",
    r"\bunable to move\b",
    r"\bslurred speech\b",
    r"\bsevere headache\b",
    r"\bsuicid(e|al)\b",
    r"\bkill (myself|my self)\b",
    r"\bself[- ]harm\b",
    r"\boverdose\b",
]
_EMERGENCY_RE = re.compile("|".join(_EMERGENCY_PATTERNS), re.IGNORECASE)

EMERGENCY_REPLY = (
    "This sounds urgent. Please don't wait for me — call your doctor right "
    "now, or go to the nearest emergency room. In India you can also call "
    "**112** for emergency services or **iCall 9152987821** for mental-health "
    "support. I'm here to help you understand your report, but I can't be "
    "the right resource in an emergency."
)


def detect_emergency(message: str) -> bool:
    return bool(_EMERGENCY_RE.search(message or ""))


# ──────────────────────────────────────────────────────────────────────────────
# Starter prompts (doc-type-aware)
# ──────────────────────────────────────────────────────────────────────────────

_GENERIC_STARTERS = [
    ("Explain in simple words", "Please explain this report to me in simple everyday words."),
    ("What should I ask my doctor?", "Based on this report, what questions should I ask my doctor at my next visit?"),
    ("What do the numbers mean?", "Walk me through the numbers in this report and what each one is about."),
]

_PRESCRIPTION_STARTERS = [
    ("What do my medicines do?", "For each medicine in this prescription, explain what it treats and how to take it."),
    ("Any food or lifestyle tips?", "Are there foods, drinks, or activities I should avoid or focus on while taking these medicines?"),
    ("When should I feel better?", "How long does it usually take for these medicines to start working?"),
    ("What if I miss a dose?", "What should I do if I forget to take a dose?"),
]

_LAB_STARTERS = [
    ("Explain my results", "Explain each lab result in simple words — what's normal, what's a bit off, and what's concerning."),
    ("What's abnormal?", "Which of my results are outside the normal range and what could that mean?"),
    ("How do I improve these?", "What lifestyle changes could help improve the abnormal values in this report?"),
    ("Do I need to retest?", "Based on these results, is a follow-up test usually recommended and how soon?"),
]

_DISCHARGE_STARTERS = [
    ("What happened in hospital?", "Summarize my hospital stay in simple words."),
    ("What do I need to do at home?", "Walk me through the things I need to do at home after discharge."),
    ("Warning signs to watch for", "What symptoms would mean I should come back to the hospital?"),
]


def starters_for_doc_type(doc_type: str | None) -> list[dict]:
    if doc_type == "prescription":
        base = _PRESCRIPTION_STARTERS
    elif doc_type == "lab_report":
        base = _LAB_STARTERS
    elif doc_type == "discharge_summary":
        base = _DISCHARGE_STARTERS
    else:
        base = _GENERIC_STARTERS
    return [{"label": label, "prompt": prompt} for label, prompt in base]


PATIENT_DISCLAIMER = (
    "I can help you understand this report, but I'm not a doctor. Always "
    "confirm with your physician before making any medical decisions."
)


# ──────────────────────────────────────────────────────────────────────────────
# Grounding context builder
# ──────────────────────────────────────────────────────────────────────────────

def build_grounding_context(doc: MedicalDocument) -> str:
    """Compose a compact, token-efficient context block the model can cite from.

    We intentionally do NOT include the raw PDF text — it's lossy and often
    10x larger than the structured extraction while adding little signal. The
    extracted_data JSON is what Gemini already decided was the important
    content; the ai_summary is its natural-language compression of the same.
    """
    bits: list[str] = []
    bits.append(f"Document type: {doc.doc_type or 'unknown'}")
    if doc.document_date:
        bits.append(f"Document date: {doc.document_date}")
    if doc.hospital_name:
        bits.append(f"Hospital/clinic: {doc.hospital_name}")
    if doc.doctor_name:
        bits.append(f"Doctor on document: {doc.doctor_name}")
    if doc.ai_summary:
        bits.append(f"\nDoctor-facing summary: {doc.ai_summary}")
    if doc.extracted_data:
        try:
            extracted_json = json.dumps(doc.extracted_data, ensure_ascii=False, indent=2)
        except (TypeError, ValueError):
            extracted_json = str(doc.extracted_data)
        bits.append("\nStructured extraction (verbatim):\n" + extracted_json)

    ctx = "\n".join(bits)
    if len(ctx) > CONTEXT_MAX_CHARS:
        # Keep the head (metadata + summary) and trim the extraction tail.
        ctx = ctx[:CONTEXT_MAX_CHARS] + "\n…(truncated for length)"
    return ctx


# ──────────────────────────────────────────────────────────────────────────────
# Rate limiting (per patient, per doc, per day)
# ──────────────────────────────────────────────────────────────────────────────

async def count_user_messages_today(
    db: AsyncSession, patient_id, document_id,
) -> int:
    cutoff = datetime.now(timezone.utc) - timedelta(hours=24)
    q = await db.execute(
        select(func.count(DocumentChatMessage.id)).where(
            DocumentChatMessage.patient_id == patient_id,
            DocumentChatMessage.document_id == document_id,
            DocumentChatMessage.role == "user",
            DocumentChatMessage.created_at >= cutoff,
        )
    )
    return int(q.scalar() or 0)


# ──────────────────────────────────────────────────────────────────────────────
# History window
# ──────────────────────────────────────────────────────────────────────────────

async def load_recent_history(
    db: AsyncSession, patient_id, document_id, limit: int = HISTORY_WINDOW_TURNS,
) -> list[dict]:
    q = await db.execute(
        select(DocumentChatMessage)
        .where(
            DocumentChatMessage.patient_id == patient_id,
            DocumentChatMessage.document_id == document_id,
        )
        .order_by(DocumentChatMessage.created_at.desc())
        .limit(limit)
    )
    rows = list(q.scalars().all())
    rows.reverse()  # oldest first for Gemini
    return [{"role": r.role, "content": r.content} for r in rows]


# ──────────────────────────────────────────────────────────────────────────────
# The orchestrator
# ──────────────────────────────────────────────────────────────────────────────

@dataclass
class ChatReply:
    text: str
    refusal_reason: str | None   # 'red_flag' | 'rate_limit' | None


async def generate_reply(
    *,
    db: AsyncSession,
    doc: MedicalDocument,
    user_message: str,
    remaining_after_this: int,
) -> ChatReply:
    """Produce the assistant reply (does NOT persist — caller owns DB writes).

    Priority of checks:
      1. Emergency keywords  → hard-coded safety response, skip LLM entirely.
      2. Otherwise           → grounded Gemini call.
    """
    if detect_emergency(user_message):
        logger.info(
            "Emergency keyword detected in chat for patient=%s doc=%s — "
            "short-circuiting to crisis response",
            doc.patient_id, doc.id,
        )
        return ChatReply(text=EMERGENCY_REPLY, refusal_reason="red_flag")

    grounding = build_grounding_context(doc)
    history = await load_recent_history(db, doc.patient_id, doc.id)

    text = await gemini_service.chat_about_document(
        system_prompt=SYSTEM_PROMPT,
        grounding_context=grounding,
        history=history,
        user_message=user_message,
    )
    return ChatReply(text=text, refusal_reason=None)
