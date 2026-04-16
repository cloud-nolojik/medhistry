"""Pydantic schemas for QR code generation and scanning."""

from datetime import datetime
from uuid import UUID
from pydantic import BaseModel, Field


class QRGenerateRequest(BaseModel):
    patient_id: UUID | None = Field(
        None,
        description="Target patient — self if omitted, or a dependent id for family members",
    )


class QRGenerateResponse(BaseModel):
    session_id: UUID
    qr_token: str
    token_version: int
    expires_at: datetime
    patient_id: UUID
    patient_name: str


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


class FollowUpNote(BaseModel):
    """One structured follow-up item extracted from a document.

    The doctor sees these grouped per-document in the briefing. The same items
    flow into the patient's `/upcoming-events` list after date-anchoring +
    stale-document filtering.
    """
    kind: str
    title: str
    due_on: str | None = None      # YYYY-MM-DD, if the document stated it explicitly
    due_hint: str | None = None    # original phrase, e.g. "in 3 months", "after 15 days"
    with_whom: str | None = None
    notes: str | None = None
    urgency: str = "routine"


class DocumentNote(BaseModel):
    """A single document's distilled clinical content, surfaced to the doctor.

    Each Gemini-extracted document already contains a doctor-targeted
    `clinical_summary`, an `overall_status`, a structured `follow_ups` list,
    etc. We pass all of this through so the doctor can actually read what
    each visit/report said.
    """
    document_id: UUID
    doc_type: str | None = None
    document_date: str | None = None  # YYYY-MM-DD
    hospital_name: str | None = None
    doctor_name: str | None = None
    doctor_specialisation: str | None = None
    clinical_summary: str | None = None  # for the doctor (technical)
    patient_summary: str | None = None   # for the patient (plain language)
    overall_status: str | None = None    # "normal" | "attention_needed" | "urgent"
    overall_status_message: str | None = None
    follow_ups: list[FollowUpNote] = Field(default_factory=list)
    symptoms: list[str] = Field(default_factory=list)
    vitals: list[dict] = Field(default_factory=list)


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
    document_notes: list[DocumentNote] = Field(default_factory=list)
    total_documents: int = 0
    session_expires_at: datetime
