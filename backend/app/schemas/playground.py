"""Pydantic schemas for the AI Playground and Re-extraction endpoints (super admin)."""

from typing import Any
from uuid import UUID
from pydantic import BaseModel, Field


class PlaygroundResult(BaseModel):
    """Result of running a single document through a chosen provider+model."""
    provider: str
    extraction_model: str
    summary_model: str
    doc_type: str | None = None
    document_date: str | None = None
    hospital_name: str | None = None
    doctor_name: str | None = None
    extracted_data: dict | None = None
    ai_summary_from_extraction: str | None = None
    patient_briefing_summary: str | None = None
    extraction_error: str | None = None
    summary_error: str | None = None
    timing_ms: dict[str, int | None] = Field(default_factory=dict)


class ReextractRequest(BaseModel):
    """Trigger re-extraction across a filtered set of documents.

    Filter precedence (any combination, all optional):
      - patient_ids: re-extract docs belonging to ANY of these patients
      - patient_id : single-patient shortcut (kept for backward compat)
      - document_ids: re-extract these specific documents only
      - none of the above → re-extract every document in the system
    """
    provider: str = "gemini"  # "gemini" | "medgemma"
    extraction_model: str | None = None  # optional override (e.g. "gemini-2.5-flash")
    patient_id: UUID | None = None              # single-patient shortcut
    patient_ids: list[UUID] | None = None       # multi-patient batch
    document_ids: list[UUID] | None = None
    rebuild_patient_summaries: bool = True
    dry_run: bool = False


class ReextractAuditEntry(BaseModel):
    document_id: str
    patient_id: str
    provider: str
    extraction_model: str | None = None
    status: str  # "ok" | "failed"
    error: str | None = None
    previous_doc_type: str | None = None
    new_doc_type: str | None = None
    new_meta_model: str | None = None
    diagnoses_before: int | None = None
    diagnoses_after: int | None = None
    medications_before: int | None = None
    medications_after: int | None = None
    lab_results_before: int | None = None
    lab_results_after: int | None = None
    dry_run: bool = False
    preview_extracted_data: dict | None = None

    model_config = {"extra": "allow"}


class ReextractResult(BaseModel):
    provider: str
    extraction_model: str | None = None
    dry_run: bool
    total_documents: int
    succeeded: int
    failed: int
    patients_affected: int
    patient_summaries_rebuilt: int
    audits: list[ReextractAuditEntry]
