"""Pydantic schemas for MedHistry super admin."""

from datetime import datetime
from uuid import UUID
from pydantic import BaseModel, Field


class SuperAdminCreate(BaseModel):
    email: str = Field(..., examples=["vijesh@medhistry.com"])
    name: str = Field(..., min_length=1, max_length=100, examples=["Vijesh Krishna"])
    password: str = Field(..., min_length=6, max_length=128)


class SuperAdminLogin(BaseModel):
    email: str
    password: str = Field(..., min_length=6)


class SuperAdminOut(BaseModel):
    id: UUID
    email: str
    name: str
    created_at: datetime

    model_config = {"from_attributes": True}


class SuperAdminTokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    admin: SuperAdminOut


class HospitalCreate(BaseModel):
    """Super admin creates a hospital and sets its admin credentials."""
    name: str = Field(..., min_length=1, max_length=200, examples=["Jadeva Hospital"])
    slug: str = Field(..., min_length=1, max_length=100, examples=["jadeva"])
    admin_password: str = Field(..., min_length=6, max_length=128, description="Password for hospital admin login")
    address: str | None = Field(None, examples=["123 Health Street, Bangalore"])
    city: str | None = Field(None, examples=["Bangalore"])
    state: str | None = Field(None, examples=["Karnataka"])
    phone: str | None = Field(None, examples=["+918012345678"])
    email: str | None = Field(None, examples=["admin@jadeva.in"])


class HospitalUpdate(BaseModel):
    """Super admin edits hospital details. All fields optional — only supplied fields are updated."""
    name: str | None = Field(None, min_length=1, max_length=200)
    address: str | None = Field(None)
    city: str | None = Field(None)
    state: str | None = Field(None)
    phone: str | None = Field(None)
    email: str | None = Field(None)
    admin_password: str | None = Field(None, min_length=6, max_length=128, description="Set new password (leave blank to keep current)")
