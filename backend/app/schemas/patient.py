"""Pydantic schemas for patient registration and profile."""

from datetime import datetime
from uuid import UUID
from pydantic import BaseModel, Field


# ---- OTP flow ----

class SendOTPRequest(BaseModel):
    phone: str = Field(..., min_length=10, max_length=15, examples=["+919876543210"])


class SendOTPResponse(BaseModel):
    message: str = "OTP sent"
    expires_in_seconds: int = 300
    # DEV ONLY — returned when DEBUG=true so the app can autofill
    otp: str | None = None


class VerifyOTPRequest(BaseModel):
    phone: str = Field(..., min_length=10, max_length=15)
    otp: str = Field(..., min_length=4, max_length=4)


class VerifyOTPResponse(BaseModel):
    verified: bool = True
    is_new_user: bool  # True = needs registration + PIN setup, False = existing user go to PIN
    temp_token: str  # short-lived token to authorize set-pin or complete-registration


# ---- Registration (after OTP verified, new user) ----

class CompleteRegistration(BaseModel):
    """New user fills in profile + sets 4-digit PIN after OTP verification."""
    temp_token: str
    name: str = Field(..., min_length=1, max_length=100, examples=["Priya Sharma"])
    pin: str = Field(..., min_length=4, max_length=4, examples=["1234"])
    date_of_birth: str | None = Field(None, examples=["1990-05-15"])
    gender: str | None = Field(None, examples=["Female"])
    blood_group: str | None = Field(None, examples=["B+"])
    allergies: str | None = Field(None, examples=["Penicillin"])


# ---- PIN login (existing user) ----

class PinLoginRequest(BaseModel):
    phone: str = Field(..., min_length=10, max_length=15)
    pin: str = Field(..., min_length=4, max_length=4)


# ---- Legacy schemas (kept for backward compat) ----

class PatientRegister(BaseModel):
    phone: str = Field(..., min_length=10, max_length=15, examples=["+919876543210"])
    name: str = Field(..., min_length=1, max_length=100, examples=["Priya Sharma"])
    password: str = Field(..., min_length=6, max_length=128)
    date_of_birth: str | None = Field(None, examples=["1990-05-15"])
    gender: str | None = Field(None, examples=["Female"])
    blood_group: str | None = Field(None, examples=["B+"])
    allergies: str | None = Field(None, examples=["Penicillin"])


class PatientLogin(BaseModel):
    phone: str = Field(..., min_length=10, max_length=15)
    password: str = Field(..., min_length=6)


# ---- Output ----

class PatientOut(BaseModel):
    id: UUID
    phone: str | None = None
    name: str
    date_of_birth: str | None = None
    gender: str | None = None
    blood_group: str | None = None
    allergies: str | None = None
    medical_summary: str | None = None
    managed_by: UUID | None = None
    relationship: str | None = None
    is_phone_verified: bool = False
    has_pin: bool = False  # computed — tells the app whether to show PIN screen
    created_at: datetime

    model_config = {"from_attributes": True}


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    patient: PatientOut


class DependentCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=100, examples=["Ramesh Sharma"])
    relationship: str = Field(..., min_length=1, max_length=30, examples=["father"])
    date_of_birth: str | None = Field(None, examples=["1955-08-12"])
    gender: str | None = None
    blood_group: str | None = None
    allergies: str | None = None
    phone: str | None = Field(None, max_length=15)


class DependentOut(PatientOut):
    pass


class FamilyListResponse(BaseModel):
    primary: PatientOut
    dependents: list[DependentOut]
