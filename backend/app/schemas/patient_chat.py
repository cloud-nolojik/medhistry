"""Pydantic schemas for patient-level AI chat (across all documents)."""

from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field


class PatientChatMessageOut(BaseModel):
    id: str
    role: str                           # "user" | "assistant"
    content: str
    refusal_reason: str | None = None
    created_at: datetime

    model_config = {"from_attributes": True, "populate_by_name": True}


class PatientChatSendRequest(BaseModel):
    message: str = Field(min_length=1, max_length=2000)


class PatientChatRecordEntry(BaseModel):
    """A single pre-formed message to save verbatim (no AI generation)."""
    role: str  # "user" | "assistant"
    content: str = Field(min_length=1, max_length=10000)


class PatientChatRecordRequest(BaseModel):
    """Save a batch of messages directly — used when injecting doc summaries inline."""
    messages: list[PatientChatRecordEntry] = Field(min_length=1, max_length=10)


class PatientChatSendResponse(BaseModel):
    """Matches what the mobile ChatSendResponse expects."""
    user_message: PatientChatMessageOut
    assistant_message: PatientChatMessageOut
    remaining_messages_today: int
