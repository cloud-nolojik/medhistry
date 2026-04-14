"""Pydantic schemas for doctor auth.

Doctor auth is OTP-based (like patients):
  1. POST /doctors/send-otp        → generates 4-digit OTP for phone
  2. POST /doctors/verify-otp      → verifies OTP
        - returning doctor: returns access_token + doctor profile (logged in)
        - new doctor:       returns temp_token for use during registration
  3. POST /doctors/complete-registration (new users only):
        temp_token + invite_code + optional profile overrides → account created

A doctor must have a valid pending invitation from a hospital admin for step 3.
"""

from datetime import datetime
from uuid import UUID
from pydantic import BaseModel, Field


class DoctorSendOTPRequest(BaseModel):
    phone: str = Field(..., min_length=10, max_length=15, examples=["+919812345678"])


class DoctorSendOTPResponse(BaseModel):
    message: str
    expires_in_seconds: int
    # DEV ONLY — returned so the app can autofill while SMS is not wired up.
    otp: str | None = None


class DoctorVerifyOTPRequest(BaseModel):
    phone: str = Field(..., min_length=10, max_length=15)
    otp: str = Field(..., min_length=4, max_length=4)


class DoctorOut(BaseModel):
    id: UUID
    hospital_id: UUID
    hospital_name: str | None = None
    phone: str
    name: str
    specialisation: str | None = None
    license_number: str | None = None
    email: str | None = None
    created_at: datetime

    model_config = {"from_attributes": True}


class DoctorVerifyOTPResponse(BaseModel):
    verified: bool
    is_new_user: bool
    # Populated when the doctor already exists (i.e. login). Empty for new users.
    access_token: str | None = None
    doctor: DoctorOut | None = None
    # Populated for new users; used in complete-registration.
    temp_token: str | None = None


class DoctorCompleteRegistration(BaseModel):
    temp_token: str = Field(..., description="Short-lived token from verify-otp")
    invite_code: str = Field(..., min_length=4, max_length=16)
    # Optional overrides — if not supplied, values from the invitation are used.
    name: str | None = Field(None, min_length=1, max_length=100)
    specialisation: str | None = None
    license_number: str | None = None
    email: str | None = None


class DoctorTokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    doctor: DoctorOut


class DoctorDashboardBriefing(BaseModel):
    id: UUID
    patient_id: UUID
    patient_name: str
    method: str
    accessed_at: datetime


class DoctorDashboard(BaseModel):
    today_count: int
    week_count: int
    avg_briefing_seconds: int | None = None
    recent_briefings: list[DoctorDashboardBriefing] = []
