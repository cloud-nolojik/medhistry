"""Pydantic schemas for doctor registration (via invitation), login, and profile."""

from datetime import datetime
from uuid import UUID
from pydantic import BaseModel, Field


class DoctorRegisterViaInvite(BaseModel):
    invite_code: str = Field(..., description="Invitation code received from hospital admin")
    phone: str = Field(..., min_length=10, max_length=15, examples=["+919812345678"])
    name: str = Field(..., min_length=1, max_length=100, examples=["Dr. Arun Mehta"])
    password: str = Field(..., min_length=6, max_length=128)
    specialisation: str | None = Field(None, examples=["General Medicine"])
    license_number: str | None = Field(None, examples=["KA-MCI-12345"])
    email: str | None = Field(None, examples=["arun.mehta@jadeva.in"])


class DoctorLogin(BaseModel):
    phone: str = Field(..., min_length=10, max_length=15)
    password: str = Field(..., min_length=6)


class DoctorOut(BaseModel):
    id: UUID
    hospital_id: UUID
    phone: str
    name: str
    specialisation: str | None = None
    license_number: str | None = None
    email: str | None = None
    created_at: datetime

    model_config = {"from_attributes": True}


class DoctorTokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    doctor: DoctorOut
