"""Pydantic schemas for medical document upload and processing."""

from datetime import datetime
from uuid import UUID
from pydantic import BaseModel, Field


class DocumentOut(BaseModel):
    id: UUID
    patient_id: UUID
    filename: str
    file_type: str
    doc_type: str | None = None
    ai_summary: str | None = None
    extracted_data: dict | None = None
    processing_status: str
    document_date: str | None = None
    hospital_name: str | None = None
    doctor_name: str | None = None
    created_at: datetime

    model_config = {"from_attributes": True}


class DocumentListOut(BaseModel):
    documents: list[DocumentOut]
    total: int


class UploadUrlRequest(BaseModel):
    """Mobile asks the backend to mint a SAS upload URL for a single document."""
    filename: str
    content_type: str  # e.g. "application/pdf", "image/jpeg"
    file_size_bytes: int
    hospital_id: str | None = None  # optional context for folder hierarchy
    doctor_id: str | None = None    # optional context for folder hierarchy
    # Target patient — self if omitted, else a dependent managed by the
    # caller. Lets son upload Dad's old reports under Dad's profile.
    patient_id: UUID | None = None


class UploadUrlResponse(BaseModel):
    """Response from POST /documents/upload-url.

    Mobile uses `upload_url` to PUT the file directly to Azure, then calls
    POST /documents/confirm with `document_id` to trigger backend processing.
    """
    document_id: UUID
    upload_url: str
    blob_path: str
    expires_in_seconds: int


class ConfirmUploadRequest(BaseModel):
    document_id: UUID


class FileUrlResponse(BaseModel):
    """Short-lived read SAS URL for viewing the original document."""
    url: str
    expires_in_seconds: int


class PatientHealthSummary(BaseModel):
    """Aggregated health summary across all uploaded documents."""
    patient_id: UUID
    total_documents: int
    medications: list[dict] = Field(default_factory=list)
    diagnoses: list[str] = Field(default_factory=list)
    allergies: list[str] = Field(default_factory=list)
    vitals: list[dict] = Field(default_factory=list)
    lab_results: list[dict] = Field(default_factory=list)
    overall_summary: str | None = None  # Doctor-oriented aggregated summary (across ALL docs)
    patient_summary: str | None = None  # Patient-friendly aggregated summary (across ALL docs).
    #   Generated alongside overall_summary on every upload so the patient Home screen
    #   reflects cumulative state, not just the most recent document.
    overall_status: str | None = None  # "all_good" | "attention_needed" | "critical"
    overall_status_message: str | None = None  # Short 1-line patient message
    last_updated: datetime | None = None
