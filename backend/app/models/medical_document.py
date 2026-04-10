"""MedicalDocument model — stores uploaded medical documents and their AI-extracted data."""

import uuid
from datetime import datetime, timezone

from sqlalchemy import String, DateTime, Text, Boolean, ForeignKey, Integer, JSON
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy.dialects.postgresql import UUID

from app.core.database import Base


class MedicalDocument(Base):
    __tablename__ = "medical_documents"

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    patient_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("patients.id"), nullable=False, index=True
    )

    # File info
    filename: Mapped[str] = mapped_column(String(255), nullable=False)
    file_path: Mapped[str] = mapped_column(String(500), nullable=False)
    file_type: Mapped[str] = mapped_column(String(50), nullable=False)  # pdf, jpg, png
    file_size_bytes: Mapped[int] = mapped_column(Integer, nullable=False)

    # Document classification
    doc_type: Mapped[str | None] = mapped_column(String(50))  # prescription, lab_report, discharge_summary, etc.

    # AI-extracted structured data (from Gemini)
    extracted_text: Mapped[str | None] = mapped_column(Text)  # Raw text from document
    extracted_data: Mapped[dict | None] = mapped_column(JSON)  # Structured JSON: medications, diagnoses, vitals, etc.
    ai_summary: Mapped[str | None] = mapped_column(Text)  # Human-readable summary of this document

    # Processing status
    processing_status: Mapped[str] = mapped_column(
        String(20), default="pending"  # pending, processing, completed, failed
    )
    processing_error: Mapped[str | None] = mapped_column(Text)

    # Metadata
    document_date: Mapped[str | None] = mapped_column(String(10))  # Date on the document (YYYY-MM-DD)
    hospital_name: Mapped[str | None] = mapped_column(String(200))  # Hospital/clinic on the document
    doctor_name: Mapped[str | None] = mapped_column(String(100))  # Doctor who wrote it

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(timezone.utc)
    )
