"""Pydantic schemas for QR code generation and scanning."""

from datetime import datetime
from uuid import UUID
from pydantic import BaseModel, Field


class QRGenerateResponse(BaseModel):
    session_id: UUID
    qr_token: str
    token_version: int
    expires_at: datetime


class QRRefreshResponse(BaseModel):
    qr_token: str
    token_version: int
    expires_at: datetime


class QRScanRequest(BaseModel):
    qr_token: str = Field(..., description="Encrypted QR token scanned from patient's device")


class ShareCodeGenerateRequest(BaseModel):
    patient_id: UUID | None = Field(
        None,
        description="Target patient — self if omitted, or a dependent id for family members",
    )


class ShareCodeGenerateResponse(BaseModel):
    session_id: UUID
    share_code: str = Field(..., description="6-digit numeric code, valid 5 min, single-use")
    patient_id: UUID
    patient_name: str
    expires_at: datetime


class ShareCodeRedeemRequest(BaseModel):
    share_code: str = Field(..., min_length=6, max_length=6, pattern=r"^\d{6}$")


class PatientAccessLogEntry(BaseModel):
    id: UUID
    doctor_id: UUID
    doctor_name: str | None = None
    hospital_id: UUID | None = None
    hospital_name: str | None = None
    method: str
    accessed_at: datetime


class PatientBriefing(BaseModel):
    """What the doctor sees after scanning — the 30-second briefing card."""
    patient_id: UUID
    name: str
    age: str | None = None
    gender: str | None = None
    blood_group: str | None = None
    allergies: str | None = None
    medical_summary: str | None = None
    medications: list[dict] = Field(default_factory=list)
    diagnoses: list[str] = Field(default_factory=list)
    critical_labs: list[dict] = Field(default_factory=list)
    total_documents: int = 0
    session_expires_at: datetime
