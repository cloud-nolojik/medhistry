"""Per-document patient chat API.

Endpoints:
  POST   /documents/{doc_id}/chat           — send one message, get the reply
  GET    /documents/{doc_id}/chat           — full message history
  GET    /documents/{doc_id}/chat/starters  — doc-type-aware suggested prompts
  DELETE /documents/{doc_id}/chat           — reset the thread

All endpoints are scoped to the authenticated primary patient and validate
the target document belongs to a patient the primary can act on (self or
managed dependent) via resolve_patient_context. A doctor's QR-scan session
does NOT get write access here — chat is a patient-only capability in v1.
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_current_patient, resolve_patient_context
from app.core.database import get_db
from app.models.document_chat_message import DocumentChatMessage
from app.models.medical_document import MedicalDocument
from app.models.patient import Patient
from app.schemas.document_chat import (
    ChatMessageListOut,
    ChatMessageOut,
    ChatSendRequest,
    ChatSendResponse,
    ChatStarter,
    ChatStartersOut,
)
from app.services import document_chat_service

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/documents", tags=["document-chat"])


async def _load_doc_with_auth(
    doc_id: UUID, primary: Patient, db: AsyncSession,
) -> MedicalDocument:
    """Load the document and enforce that `primary` may act on its patient."""
    q = await db.execute(select(MedicalDocument).where(MedicalDocument.id == doc_id))
    doc = q.scalar_one_or_none()
    if doc is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Document not found")
    # Authorize via the same helper used everywhere else — raises 403 if the
    # caller can't act on this patient (not self, not a managed dependent).
    await resolve_patient_context(doc.patient_id, primary, db)
    return doc


@router.get("/{doc_id}/chat/starters", response_model=ChatStartersOut)
async def get_starters(
    doc_id: UUID,
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Doc-type-aware starter prompts for an empty chat input.

    Shown on first open so patients don't stare at a blank box. Each starter
    has a short button label (`label`) and the full text sent on tap (`prompt`).
    """
    doc = await _load_doc_with_auth(doc_id, primary, db)
    starters_raw = document_chat_service.starters_for_doc_type(doc.doc_type)
    return ChatStartersOut(
        starters=[ChatStarter(**s) for s in starters_raw],
        disclaimer=document_chat_service.PATIENT_DISCLAIMER,
    )


@router.get("/{doc_id}/chat", response_model=ChatMessageListOut)
async def list_chat(
    doc_id: UUID,
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Return the full message history for this document, oldest first."""
    doc = await _load_doc_with_auth(doc_id, primary, db)
    q = await db.execute(
        select(DocumentChatMessage)
        .where(
            DocumentChatMessage.patient_id == doc.patient_id,
            DocumentChatMessage.document_id == doc.id,
        )
        .order_by(DocumentChatMessage.created_at.asc())
    )
    rows = list(q.scalars().all())
    return ChatMessageListOut(
        messages=[ChatMessageOut.model_validate(r) for r in rows],
        total=len(rows),
    )


@router.post("/{doc_id}/chat", response_model=ChatSendResponse)
async def send_chat(
    doc_id: UUID,
    body: ChatSendRequest,
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Send one message and get the assistant reply.

    Flow:
      1. Authorize + load the document.
      2. Block if the document's processing isn't complete yet — there's
         nothing to ground the answer on.
      3. Enforce per-doc daily quota.
      4. Persist the user's message.
      5. Generate reply (red-flag short-circuit OR Gemini call).
      6. Persist the assistant's message. Return both turns.
    """
    doc = await _load_doc_with_auth(doc_id, primary, db)

    if doc.processing_status != "completed":
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=(
                "This document is still being processed. Please wait for the "
                "summary to finish before chatting about it."
            ),
        )

    user_text = body.message.strip()
    if not user_text:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Message cannot be empty.",
        )

    # Rate limit BEFORE writing anything.
    used_today = await document_chat_service.count_user_messages_today(
        db, doc.patient_id, doc.id,
    )
    if used_today >= document_chat_service.DAILY_USER_MESSAGE_QUOTA:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=(
                f"You've reached today's chat limit for this report "
                f"({document_chat_service.DAILY_USER_MESSAGE_QUOTA} messages). "
                f"Please try again tomorrow."
            ),
        )

    # Persist user turn FIRST so the quota counter is correct on retries and
    # so the message isn't lost if the model call times out.
    user_row = DocumentChatMessage(
        patient_id=doc.patient_id,
        document_id=doc.id,
        role="user",
        content=user_text,
    )
    db.add(user_row)
    await db.flush()

    reply = await document_chat_service.generate_reply(
        db=db, doc=doc, user_message=user_text,
        remaining_after_this=document_chat_service.DAILY_USER_MESSAGE_QUOTA - used_today - 1,
    )

    assistant_row = DocumentChatMessage(
        patient_id=doc.patient_id,
        document_id=doc.id,
        role="assistant",
        content=reply.text,
        refusal_reason=reply.refusal_reason,
    )
    db.add(assistant_row)
    await db.commit()
    await db.refresh(user_row)
    await db.refresh(assistant_row)

    remaining = max(
        0,
        document_chat_service.DAILY_USER_MESSAGE_QUOTA - (used_today + 1),
    )
    return ChatSendResponse(
        user_message=ChatMessageOut.model_validate(user_row),
        assistant_message=ChatMessageOut.model_validate(assistant_row),
        remaining_messages_today=remaining,
    )


@router.delete("/{doc_id}/chat", status_code=status.HTTP_204_NO_CONTENT)
async def reset_chat(
    doc_id: UUID,
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Delete the entire chat thread for this document.

    Useful if the patient wants to start fresh or remove sensitive content
    they typed by mistake. Does NOT delete the document itself.
    """
    doc = await _load_doc_with_auth(doc_id, primary, db)
    await db.execute(
        delete(DocumentChatMessage).where(
            DocumentChatMessage.patient_id == doc.patient_id,
            DocumentChatMessage.document_id == doc.id,
        )
    )
    await db.commit()
    return None
