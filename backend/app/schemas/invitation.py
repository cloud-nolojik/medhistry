"""Pydantic schemas for doctor invitations."""

from datetime import datetime
from uuid import UUID
from pydantic import BaseModel, Field


class InvitationCreate(BaseModel):
    doctor_phone: str = Field(..., min_length=10, max_length=15, examples=["+919812345678"])
    doctor_name: str = Field(..., min_length=1, max_length=100, examples=["Dr. Arun Mehta"])
    doctor_email: str | None = Field(None, examples=["arun.mehta@jadeva.in"])
    specialisation: str | None = Field(None, examples=["General Medicine"])


class InvitationOut(BaseModel):
    id: UUID
    hospital_id: UUID
    doctor_phone: str
    doctor_name: str
    doctor_email: str | None = None
    specialisation: str | None = None
    invite_code: str
    status: str
    expires_at: datetime
    created_at: datetime

    model_config = {"from_attributes": True}


class InvitationUpdate(BaseModel):
    doctor_name: str | None = None
    doctor_phone: str | None = None
    doctor_email: str | None = None
    specialisation: str | None = None


class InvitationListOut(BaseModel):
    invitations: list[InvitationOut]
    total: int
