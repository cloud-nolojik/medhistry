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
    # Computed — tells the app whether to show PIN prompt or PIN setup after
    # OTP login. True = /pin-login flow; False = send to set-pin screen.
    has_pin: bool = False
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


class DoctorVerifyInviteRequest(BaseModel):
    invite_code: str = Field(..., min_length=4, max_length=16)
    temp_token: str = Field(
        ...,
        description="Short-lived token from verify-otp. Used to enforce phone match with the invitation.",
    )


class DoctorVerifyInviteResponse(BaseModel):
    valid: bool
    hospital_name: str
    doctor_name: str | None = None
    specialisation: str | None = None
    doctor_phone: str


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


# ---- PIN auth (after OTP + registration) ----

class DoctorSetPinRequest(BaseModel):
    """Set a 6-digit PIN. Requires an authenticated doctor (bearer token).
    Used right after first OTP-login to establish a PIN for subsequent opens."""
    pin: str = Field(..., min_length=6, max_length=6, examples=["123456"])


class DoctorPinLoginRequest(BaseModel):
    """Existing doctor on same device re-authenticates with PIN."""
    phone: str = Field(..., min_length=10, max_length=15)
    pin: str = Field(..., min_length=6, max_length=6)


class DoctorDashboardBriefing(BaseModel):
    id: UUID
    patient_id: UUID
    patient_name: str
    method: str
    accessed_at: datetime


class DoctorDashboard(BaseModel):
    today_count: int
    week_count: int
    all_time_count: int = 0
    avg_briefing_seconds: int | None = None
    recent_briefings: list[DoctorDashboardBriefing] = []


class DoctorBriefingsList(BaseModel):
    """Paginated list of the doctor's patient briefings (access-log rows)."""
    briefings: list[DoctorDashboardBriefing] = []
    total: int = 0
    has_more: bool = False
