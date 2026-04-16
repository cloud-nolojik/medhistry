"""AI Playground + Re-extraction service.

Two distinct super-admin capabilities live here:

1. **Playground** (`run_playground`)
   Runs the full extraction + per-doc summary pipeline against an in-memory file
   without touching the DB. Lets the super admin try a new provider/model on a
   sample report and see exactly what gets extracted vs what the final summary
   looks like — so they can compare options before flipping the global config.

2. **Re-extraction** (`reextract_document`, `reextract_batch`)
   Re-runs extraction on documents that are ALREADY in the DB. Used when:
     - We switch the default model (e.g. gemini → medgemma) and want to
       backfill all existing prescriptions/reports under the new model.
     - We change the extraction prompt and want to refresh historical data.
   Each run captures a small audit record (provider, model, before/after counts)
   so the super admin can verify what changed.
"""

from __future__ import annotations

import os
import tempfile
import time
import uuid
from typing import Any

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.models.medical_document import MedicalDocument
from app.models.patient import Patient
from app.services import gemini_service, medgemma_service, azure_storage_service


# ───────────────────────── helpers ─────────────────────────

async def _materialize_to_temp(file_path: str, file_type: str) -> tuple[str, bool]:
    """If file_path is an Azure blob path, download it. Return (local_path, is_temp)."""
    is_azure = settings.USE_AZURE_STORAGE and file_path.startswith("patients/")
    if not is_azure:
        return file_path, False

    data = await azure_storage_service.download_blob_bytes(file_path)
    fd, tmp = tempfile.mkstemp(suffix=f".{file_type}", prefix="reextract_")
    with os.fdopen(fd, "wb") as f:
        f.write(data)
    return tmp, True


async def _run_extractor(
    file_path: str, file_type: str, provider: str, model_override: str | None
) -> dict:
    """Call the right provider directly (no side effects)."""
    if provider == "medgemma":
        return await medgemma_service.extract_document(file_path, file_type, model_override)
    return await gemini_service.extract_document(file_path, file_type, model_override)


# ───────────────────────── playground ─────────────────────────

async def run_playground(
    file_bytes: bytes,
    file_type: str,
    provider: str,
    extraction_model: str | None,
    summary_model: str | None,
) -> dict:
    """Run the full extract + per-doc summary pipeline on an uploaded file.

    Stateless: no DB writes, no Azure uploads. Used by the super-admin
    Playground tab to A/B test models. Returns the extracted JSON, the
    raw extracted summary from the doc, and a freshly generated
    'patient briefing' summary computed by the configured summary model
    (so admins can see what would actually go into a patient's medical_summary).
    """
    fd, tmp_path = tempfile.mkstemp(suffix=f".{file_type}", prefix="playground_")
    started = time.perf_counter()
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(file_bytes)

        # Step 1: extraction
        extract_started = time.perf_counter()
        extract_result = await _run_extractor(
            tmp_path, file_type, provider, extraction_model
        )
        extract_ms = int((time.perf_counter() - extract_started) * 1000)

        # Step 2: summary (always uses Gemini Flash by default unless overridden)
        # We feed the just-extracted data through the aggregated-summary prompt
        # so the admin sees what a "first document" patient briefing would look
        # like under this combination. The service returns BOTH a clinical
        # (doctor-facing) and patient-facing narrative in a single call; we
        # surface both so admins can A/B test tone on both audiences.
        clinical_summary: str | None = None
        patient_briefing: str | None = None
        summary_ms: int | None = None
        summary_error: str | None = None
        if extract_result.get("extracted_data"):
            try:
                s_started = time.perf_counter()
                summary_dict = await gemini_service.generate_aggregated_summary(
                    [extract_result["extracted_data"]],
                    model_override=summary_model,
                )
                clinical_summary = summary_dict.get("clinical")
                patient_briefing = summary_dict.get("patient")
                summary_ms = int((time.perf_counter() - s_started) * 1000)
            except Exception as e:
                summary_error = str(e)

        return {
            "provider": provider,
            "extraction_model": extract_result.get("_meta", {}).get("model")
            or extraction_model
            or "(default)",
            "summary_model": summary_model or settings.GEMINI_MODEL,
            "doc_type": extract_result.get("doc_type"),
            "document_date": extract_result.get("document_date"),
            "hospital_name": extract_result.get("hospital_name"),
            "doctor_name": extract_result.get("doctor_name"),
            "extracted_data": extract_result.get("extracted_data"),
            "ai_summary_from_extraction": extract_result.get("ai_summary"),
            "patient_briefing_summary": clinical_summary,
            "patient_facing_summary": patient_briefing,
            "summary_error": summary_error,
            "extraction_error": extract_result.get("error"),
            "timing_ms": {
                "extraction": extract_ms,
                "summary": summary_ms,
                "total": int((time.perf_counter() - started) * 1000),
            },
        }
    finally:
        try:
            os.unlink(tmp_path)
        except OSError:
            pass


# ───────────────────────── re-extraction ─────────────────────────

async def reextract_document(
    db: AsyncSession,
    doc: MedicalDocument,
    provider: str,
    extraction_model: str | None,
    dry_run: bool = False,
) -> dict:
    """Re-run extraction on a single existing document.

    Returns an audit record. If `dry_run=True`, the new data is computed and
    returned but the DB row is NOT updated — used for previewing what would
    change before committing.
    """
    audit: dict[str, Any] = {
        "document_id": str(doc.id),
        "patient_id": str(doc.patient_id),
        "provider": provider,
        "extraction_model": extraction_model,
        "previous_doc_type": doc.doc_type,
        "previous_processing_status": doc.processing_status,
        "dry_run": dry_run,
        "status": "ok",
        "error": None,
    }

    local_path, is_temp = await _materialize_to_temp(doc.file_path, doc.file_type)
    try:
        result = await _run_extractor(local_path, doc.file_type, provider, extraction_model)
    except Exception as e:
        audit["status"] = "failed"
        audit["error"] = str(e)
        return audit
    finally:
        if is_temp:
            try:
                os.unlink(local_path)
            except OSError:
                pass

    if result.get("error"):
        audit["status"] = "failed"
        audit["error"] = result["error"]
        return audit

    audit["new_doc_type"] = result.get("doc_type")
    audit["new_meta_model"] = result.get("_meta", {}).get("model")

    # Diff signal: did the extracted_data change at all?
    new_data = result.get("extracted_data") or {}
    old_data = doc.extracted_data or {}
    audit["diagnoses_before"] = len(old_data.get("diagnoses", []) or [])
    audit["diagnoses_after"] = len(new_data.get("diagnoses", []) or [])
    audit["medications_before"] = len(old_data.get("medications", []) or [])
    audit["medications_after"] = len(new_data.get("medications", []) or [])
    audit["lab_results_before"] = len(old_data.get("lab_results", []) or [])
    audit["lab_results_after"] = len(new_data.get("lab_results", []) or [])

    if dry_run:
        audit["preview_extracted_data"] = new_data
        return audit

    # Commit the new extraction
    doc.extracted_text = result.get("extracted_text")
    doc.extracted_data = new_data
    doc.ai_summary = result.get("ai_summary")
    doc.doc_type = result.get("doc_type")
    doc.document_date = result.get("document_date") or doc.document_date
    doc.hospital_name = result.get("hospital_name") or doc.hospital_name
    doc.doctor_name = result.get("doctor_name") or doc.doctor_name
    doc.processing_status = "completed"
    doc.processing_error = None
    await db.commit()
    await db.refresh(doc)
    return audit


async def reextract_batch(
    db: AsyncSession,
    provider: str,
    extraction_model: str | None,
    patient_id: uuid.UUID | None = None,
    patient_ids: list[uuid.UUID] | None = None,
    document_ids: list[uuid.UUID] | None = None,
    rebuild_patient_summaries: bool = True,
    dry_run: bool = False,
) -> dict:
    """Re-extract a batch of documents.

    Filters (any combination, all optional):
      - patient_id: shortcut for a single patient's docs
      - patient_ids: docs belonging to ANY of these patients (multi-patient batch)
      - document_ids: only these specific docs

    If no filter is given, EVERY document in the system is re-extracted.

    If `rebuild_patient_summaries=True` and not dry_run, after each affected
    patient's docs are re-extracted we trigger a full summary rebuild for that
    patient so the briefing reflects the new extraction.
    """
    # Merge patient_id (singular) into patient_ids (plural) for a single code path
    merged_patient_ids: list[uuid.UUID] = list(patient_ids or [])
    if patient_id is not None and patient_id not in merged_patient_ids:
        merged_patient_ids.append(patient_id)

    # Build query
    stmt = select(MedicalDocument)
    if merged_patient_ids:
        stmt = stmt.where(MedicalDocument.patient_id.in_(merged_patient_ids))
    if document_ids:
        stmt = stmt.where(MedicalDocument.id.in_(document_ids))

    result = await db.execute(stmt)
    docs = result.scalars().all()

    audits: list[dict] = []
    affected_patients: set[uuid.UUID] = set()
    succeeded = 0
    failed = 0
    for doc in docs:
        audit = await reextract_document(
            db, doc, provider, extraction_model, dry_run=dry_run
        )
        audits.append(audit)
        if audit["status"] == "ok":
            succeeded += 1
            affected_patients.add(doc.patient_id)
        else:
            failed += 1

    # Rebuild patient summaries (full rebuild path) — only on a real run
    summaries_rebuilt = 0
    if rebuild_patient_summaries and not dry_run and affected_patients:
        # Local import to avoid circular: documents -> playground -> documents
        from app.api.documents import _update_patient_summary

        for pid in affected_patients:
            try:
                await _update_patient_summary(pid, db, new_doc_data=None)
                summaries_rebuilt += 1
            except Exception:
                pass
        await db.commit()

    return {
        "provider": provider,
        "extraction_model": extraction_model,
        "dry_run": dry_run,
        "total_documents": len(docs),
        "succeeded": succeeded,
        "failed": failed,
        "patients_affected": len(affected_patients),
        "patient_summaries_rebuilt": summaries_rebuilt,
        "audits": audits,
    }
