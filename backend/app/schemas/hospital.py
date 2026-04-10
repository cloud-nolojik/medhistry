"""Pydantic schemas for hospital login and profile.

Note: Hospital creation is done by super admin (see super_admin.py schemas).
"""

from datetime import datetime
from uuid import UUID
from pydantic import BaseModel, Field


class HospitalLogin(BaseModel):
    slug: str = Field(..., min_length=1)
    password: str = Field(..., min_length=6)


class HospitalOut(BaseModel):
    id: UUID
    name: str
    slug: str
    address: str | None = None
    city: str | None = None
    state: str | None = None
    phone: str | None = None
    email: str | None = None
    created_at: datetime

    model_config = {"from_attributes": True}


class HospitalTokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    hospital: HospitalOut
