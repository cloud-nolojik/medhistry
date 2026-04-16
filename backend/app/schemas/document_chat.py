"""Pydantic schemas for per-document patient chat."""

from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field


class ChatMessageOut(BaseModel):
    id: UUID
    document_id: UUID
    role: str                          # "user" | "assistant"
    content: str
    refusal_reason: str | None = None  # "red_flag" | "rate_limit" | "out_of_scope" | None
    created_at: datetime

    model_config = {"from_attributes": True}


class ChatMessageListOut(BaseModel):
    messages: list[ChatMessageOut]
    total: int


class ChatSendRequest(BaseModel):
    """Body for POST /documents/{id}/chat — the user's next message."""
    message: str = Field(min_length=1, max_length=2000)


class ChatSendResponse(BaseModel):
    """Returns both turns so the client doesn't need a second round-trip."""
    user_message: ChatMessageOut
    assistant_message: ChatMessageOut
    remaining_messages_today: int  # simple quota visibility


class ChatStarter(BaseModel):
    """One suggested starter prompt shown under the empty chat input."""
    label: str          # short button label, e.g. "Explain in simple words"
    prompt: str         # the full prompt text we send on tap


class ChatStartersOut(BaseModel):
    """Doc-type-aware starter prompts to convert the 'blank box' problem."""
    starters: list[ChatStarter]
    disclaimer: str     # medical disclaimer the client shows above the input
