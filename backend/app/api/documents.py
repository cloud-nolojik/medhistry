"""Medical document upload, processing, and retrieval endpoints.

Storage architecture (Azure Blob, direct-to-cloud upload)
---------------------------------------------------------
Files live in Azure Blob Storage, NOT on the backend disk. Mobile clients never
hold the Azure connection string — the backend mints short-lived SAS URLs.

Upload flow:
  1. POST /documents/upload-url       → backend mints write-SAS, creates DB row
                                        in `pending_upload` state, returns URL
  2. Mobile PUTs file bytes to Azure  → backend bandwidth = zero
  3. POST /documents/confirm          → flips row to `pending`, kicks off
                                        background extraction
  4. Background task downloads blob   → passes bytes to Gemini extractor
  5. GET  /documents/{id}/file        → returns short-lived read-SAS for viewing

Folder layout in the container:
    patients/{patient_id}/hospitals/{hospital_id|self}/doctors/{doctor_id|self}/{doc_id}.{ext}

AI pipeline (provider-pluggable):
  1. EXTRACTION → structured medical JSON from PDF/image.
     Default: Gemini 2.5 Pro (high accuracy on structured extraction).
     Standby: MedGemma 1.5 4B on Vertex AI (set EXTRACTION_PROVIDER=medgemma).
  2. SUMMARY → patient briefing via Gemini 2.5 Flash, with incremental merge
     so cost stays constant per upload regardless of total doc count.
"""

import uuid
import os
import logging
from fastapi import APIRouter, Depends, HTTPException, status, BackgroundTasks, Query
from sqlalchemy import select, func, text, delete as sql_delete
from sqlalchemy.ext.asyncio import AsyncSession

logger = logging.getLogger(__name__)

from app.core.config import settings
from app.core.database import get_db
from app.models.patient import Patient
from app.models.medical_document import MedicalDocument
from app.models.document_chat_message import DocumentChatMessage
from app.models.patient_upcoming_event import PatientUpcomingEvent
from app.schemas.document import (
    DocumentOut,
    DocumentListOut,
    PatientHealthSummary,
    UploadUrlRequest,
    UploadUrlResponse,
    ConfirmUploadRequest,
    FileUrlResponse,
)
from app.services import gemini_service, medgemma_service, azure_storage_service
from app.services.gemini_service import (
    generate_aggregated_summary,
    generate_incremental_summary,
)


async def extract_document(
    file_path: str,
    file_type: str,
    provider_override: str | None = None,
    model_override: str | None = None,
) -> dict:
    """Dispatch document extraction to the configured provider.

    Provider is chosen by EXTRACTION_PROVIDER setting (or per-call override):
      - "gemini"   → Gemini 2.5 Pro (default, no infra to manage)
      - "medgemma" → MedGemma 1.5 4B on Vertex AI (when endpoint is deployed)

    `provider_override` and `model_override` are used by the super-admin
    Playground and Re-extract endpoints to try different providers/models
    without changing global settings.
    """
    provider = (provider_override or settings.EXTRACTION_PROVIDER or "gemini").lower()
    if provider == "medgemma":
        return await medgemma_service.extract_document(file_path, file_type, model_override)
    return await gemini_service.extract_document(file_path, file_type, model_override)
from app.api.deps import get_current_patient, resolve_patient_context

router = APIRouter(prefix="/documents", tags=["documents"])

ALLOWED_TYPES = {"application/pdf", "image/jpeg", "image/png", "image/jpg"}
TYPE_MAP = {"application/pdf": "pdf", "image/jpeg": "jpg", "image/png": "png", "image/jpg": "jpg"}


async def _materialize_blob_to_temp(blob_path: str, file_type: str) -> str:
    """Download an Azure blob to a temp file on the backend so the extractor can read it.

    Returns the local temp path. Caller is responsible for deletion. We use a temp
    file (not in-memory) because the extractor services accept file paths today.
    """
    import tempfile

    data = await azure_storage_service.download_blob_bytes(blob_path)
    fd, tmp_path = tempfile.mkstemp(suffix=f".{file_type}", prefix="medhistry_")
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(data)
    except Exception:
        try:
            os.unlink(tmp_path)
        except OSError:
            pass
        raise
    return tmp_path


async def _process_and_update(doc_id: uuid.UUID, file_path: str, file_type: str, patient_id: uuid.UUID):
    """Background task: extract structured data, then update patient summary.

    `file_path` is either a local disk path (legacy) or an Azure blob path
    (new flow). We detect which by checking USE_AZURE_STORAGE and whether the
    path starts with `patients/` (the Azure layout prefix).
    """
    from app.core.database import async_session

    async with async_session() as db:
        result = await db.execute(select(MedicalDocument).where(MedicalDocument.id == doc_id))
        doc = result.scalar_one_or_none()
        if not doc:
            return

        doc.processing_status = "processing"
        await db.commit()

        local_temp_path: str | None = None
        try:
            # If the file lives in Azure, download it to a temp path first.
            extractor_path = file_path
            is_azure_blob = settings.USE_AZURE_STORAGE and file_path.startswith("patients/")
            if is_azure_blob:
                local_temp_path = await _materialize_blob_to_temp(file_path, file_type)
                extractor_path = local_temp_path

            # Step 1: extractor produces structured medical data
            ai_result = await extract_document(extractor_path, file_type)

            doc.extracted_text = ai_result.get("extracted_text")
            doc.extracted_data = ai_result.get("extracted_data")
            doc.ai_summary = ai_result.get("ai_summary")
            doc.doc_type = ai_result.get("doc_type")
            doc.document_date = ai_result.get("document_date")
            doc.hospital_name = ai_result.get("hospital_name")
            doc.doctor_name = ai_result.get("doctor_name")
            doc.processing_status = "completed" if not ai_result.get("error") else "failed"
            doc.processing_error = ai_result.get("error")

            # Step 2: Gemini updates the patient's aggregated medical_summary
            # (incremental merge if possible, full rebuild on first doc or fallback)
            await _update_patient_summary(patient_id, db, new_doc_data=doc.extracted_data)

            # Step 3: sync structured follow-ups from this document into the
            # patient's upcoming-events list. Parses relative phrases like
            # "after 15 days" against the DOCUMENT date, and drops items when
            # the document itself is too old (so uploading an ancient record
            # doesn't resurrect expired reminders).
            from app.services.upcoming_events_service import sync_upcoming_events_for_document
            from datetime import date as _date
            doc_date: _date | None = None
            if doc.document_date:
                try:
                    doc_date = _date.fromisoformat(doc.document_date)
                except ValueError:
                    doc_date = None
            follow_ups_raw = (doc.extracted_data or {}).get("follow_ups") or []
            await sync_upcoming_events_for_document(
                db=db,
                patient_id=patient_id,
                document_id=doc.id,
                document_date=doc_date,
                follow_ups=follow_ups_raw,
            )

            await db.commit()
        except Exception as e:
            doc.processing_status = "failed"
            doc.processing_error = str(e)
            await db.commit()
        finally:
            if local_temp_path:
                try:
                    os.unlink(local_temp_path)
                except OSError:
                    pass


def _patient_lock_key(patient_id: uuid.UUID) -> int:
    """Derive a stable int64 advisory-lock key from a patient UUID.

    PostgreSQL advisory locks use a bigint key. We take the first 8 bytes of the
    UUID and convert to a signed 64-bit int — unique enough per patient.
    """
    return int.from_bytes(patient_id.bytes[:8], byteorder="big", signed=True)


async def _update_patient_summary(
    patient_id: uuid.UUID,
    db: AsyncSession,
    new_doc_data: dict | None = None,
):
    """Update the patient's overall medical summary.

    Uses a PostgreSQL transaction-level advisory lock keyed to the patient ID so
    that concurrent uploads for the SAME patient are serialized — the second task
    waits until the first finishes writing the summary, then merges on top of the
    already-updated version. Uploads for DIFFERENT patients run fully in parallel.

    Optimized: if `new_doc_data` is provided AND the patient already has a summary,
    we merge the new document's data into the existing summary (constant-cost call).
    Otherwise we do a full rebuild from all completed documents (used for first doc
    or as a fallback when incremental merge fails).
    """
    # Acquire an advisory lock scoped to this patient — blocks until available.
    # pg_advisory_xact_lock releases automatically when the transaction commits.
    # Postgres-only: SQLite (used in tests) doesn't support advisory locks, so we
    # skip the call there. Single-process test runs don't need inter-process
    # serialization anyway; production Postgres keeps the safety guarantee.
    bind = db.get_bind() if hasattr(db, "get_bind") else None
    dialect_name = getattr(getattr(bind, "dialect", None), "name", None) if bind else None
    if dialect_name == "postgresql":
        lock_key = _patient_lock_key(patient_id)
        await db.execute(text("SELECT pg_advisory_xact_lock(:key)"), {"key": lock_key})
        logger.info("Acquired summary lock for patient %s (key=%s)", patient_id, lock_key)
    else:
        logger.debug(
            "Skipping advisory lock for patient %s on non-Postgres dialect %r",
            patient_id, dialect_name,
        )

    # Re-read patient AFTER acquiring the lock so we see the latest summary
    # (a concurrent task may have committed an update while we were waiting).
    patient_result = await db.execute(select(Patient).where(Patient.id == patient_id))
    patient = patient_result.scalar_one_or_none()
    if not patient:
        return

    # Count completed documents for this patient
    count_result = await db.execute(
        select(func.count(MedicalDocument.id)).where(
            MedicalDocument.patient_id == patient_id,
            MedicalDocument.processing_status == "completed",
        )
    )
    total_doc_count = count_result.scalar() or 0

    # Incremental path: existing summary + new doc data + not the very first doc.
    # generate_incremental_summary now returns a dict {"clinical": str, "patient": str|None}
    # so both the doctor-facing and patient-facing aggregated summaries stay in sync.
    if new_doc_data and patient.medical_summary and total_doc_count > 1:
        try:
            result_dict = await generate_incremental_summary(
                patient.medical_summary,
                patient.patient_summary,
                new_doc_data,
                total_doc_count,
            )
            patient.medical_summary = result_dict["clinical"]
            if result_dict.get("patient"):
                patient.patient_summary = result_dict["patient"]
            logger.info("Incremental summary merge done for patient %s (doc #%d)", patient_id, total_doc_count)
            return
        except Exception:
            logger.warning("Incremental merge failed for patient %s, falling back to full rebuild", patient_id)

    # Full rebuild path: first document, no existing summary, or fallback
    result = await db.execute(
        select(MedicalDocument).where(
            MedicalDocument.patient_id == patient_id,
            MedicalDocument.processing_status == "completed",
        ).order_by(MedicalDocument.document_date.asc().nullslast(), MedicalDocument.created_at.asc())
    )
    docs = result.scalars().all()

    if not docs:
        # No completed documents left — clear both summaries so stale text isn't
        # shown after the last document is deleted.
        patient.medical_summary = None
        patient.patient_summary = None
        return

    all_data = [doc.extracted_data for doc in docs if doc.extracted_data]
    summary_dict = await generate_aggregated_summary(all_data)
    patient.medical_summary = summary_dict["clinical"]
    if summary_dict.get("patient"):
        patient.patient_summary = summary_dict["patient"]
    logger.info("Full summary rebuild done for patient %s (%d docs)", patient_id, len(all_data))


@router.post("/upload-url", response_model=UploadUrlResponse, status_code=status.HTTP_201_CREATED)
async def request_upload_url(
    payload: UploadUrlRequest,
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Mint a short-lived SAS upload URL for direct-to-Azure file upload.

    Mobile flow:
        1. Call this endpoint with file metadata (and optional patient_id
           to upload on behalf of a dependent family member).
        2. PUT the file bytes to `upload_url` with header `x-ms-blob-type: BlockBlob`.
        3. Call POST /documents/confirm with `document_id` to trigger processing.

    The SAS is scoped to a single blob path and expires in
    AZURE_SAS_UPLOAD_TTL_MINUTES (default 5 minutes).
    """
    if payload.content_type not in ALLOWED_TYPES:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"File type {payload.content_type} not supported. Use PDF, JPG, or PNG.",
        )
    if payload.file_size_bytes > settings.MAX_FILE_SIZE_MB * 1024 * 1024:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"File too large. Maximum {settings.MAX_FILE_SIZE_MB}MB.",
        )
    if not settings.USE_AZURE_STORAGE:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Azure Blob Storage is not enabled. Set USE_AZURE_STORAGE=true.",
        )

    # Resolve target patient — self or an authorized dependent
    target = await resolve_patient_context(
        payload.patient_id or primary.id, primary, db,
    )

    file_ext = TYPE_MAP.get(payload.content_type, "bin")
    document_id = uuid.uuid4()
    blob_path = azure_storage_service.build_blob_path(
        patient_id=str(target.id),
        document_id=str(document_id),
        file_ext=file_ext,
        hospital_id=payload.hospital_id,
        doctor_id=payload.doctor_id,
    )

    try:
        upload_url = azure_storage_service.generate_upload_sas(blob_path, payload.content_type)
    except RuntimeError as e:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(e))

    # Create DB row in pending_upload state — file_path stores the BLOB path
    doc = MedicalDocument(
        id=document_id,
        patient_id=target.id,
        filename=payload.filename,
        file_path=blob_path,
        file_type=file_ext,
        file_size_bytes=payload.file_size_bytes,
        processing_status="pending_upload",
    )
    db.add(doc)
    await db.commit()

    return UploadUrlResponse(
        document_id=document_id,
        upload_url=upload_url,
        blob_path=blob_path,
        expires_in_seconds=settings.AZURE_SAS_UPLOAD_TTL_MINUTES * 60,
    )


async def _authorized_patient_ids(
    primary: Patient, db: AsyncSession,
) -> list[uuid.UUID]:
    """Return [primary.id, ...all managed dependents]. Used for list queries
    where we want to scope by 'everything this account can see'."""
    result = await db.execute(
        select(Patient.id).where(
            Patient.managed_by == primary.id, Patient.is_active == True,
        )
    )
    ids = [primary.id] + [r[0] for r in result.all()]
    return ids


async def _load_doc_for_primary(
    document_id: uuid.UUID, primary: Patient, db: AsyncSession,
) -> MedicalDocument:
    """Load a document if it belongs to the primary OR one of their dependents."""
    allowed = await _authorized_patient_ids(primary, db)
    result = await db.execute(
        select(MedicalDocument).where(
            MedicalDocument.id == document_id,
            MedicalDocument.patient_id.in_(allowed),
        )
    )
    doc = result.scalar_one_or_none()
    if not doc:
        raise HTTPException(status_code=404, detail="Document not found")
    return doc


@router.post("/confirm", response_model=DocumentOut)
async def confirm_upload(
    payload: ConfirmUploadRequest,
    background_tasks: BackgroundTasks,
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Confirm the file finished uploading to Azure and start background extraction."""
    doc = await _load_doc_for_primary(payload.document_id, primary, db)
    if doc.processing_status != "pending_upload":
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=f"Document is in state '{doc.processing_status}', expected 'pending_upload'",
        )

    doc.processing_status = "pending"
    await db.commit()
    await db.refresh(doc)

    background_tasks.add_task(
        _process_and_update, doc.id, doc.file_path, doc.file_type, doc.patient_id
    )

    return DocumentOut.model_validate(doc)


@router.get("/{document_id}/file", response_model=FileUrlResponse)
async def get_document_file_url(
    document_id: uuid.UUID,
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Get a short-lived read SAS URL to view the original prescription/document.

    Mobile loads the file directly from Azure using the returned URL — backend
    bandwidth is zero. URL expires in AZURE_SAS_READ_TTL_MINUTES (default 15).
    Primary can view documents belonging to themselves or any dependent.
    """
    doc = await _load_doc_for_primary(document_id, primary, db)
    if not doc.file_path:
        raise HTTPException(status_code=410, detail="File not available")

    # Azure-stored doc → mint a read SAS. Legacy local-disk doc → not supported here.
    if not (settings.USE_AZURE_STORAGE and doc.file_path.startswith("patients/")):
        raise HTTPException(
            status_code=status.HTTP_410_GONE,
            detail="This document is not stored in Azure and cannot be served via SAS URL.",
        )

    try:
        url = azure_storage_service.generate_read_sas(doc.file_path)
    except RuntimeError as e:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(e))

    return FileUrlResponse(
        url=url,
        expires_in_seconds=settings.AZURE_SAS_READ_TTL_MINUTES * 60,
    )


@router.get("/", response_model=DocumentListOut)
async def list_documents(
    patient_id: uuid.UUID | None = Query(
        None, description="Optional filter — specific dependent. Omit for primary only."
    ),
    include_family: bool = Query(
        False, description="If true, include documents from all managed dependents."
    ),
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """List uploaded documents.

    Defaults to the primary's own documents. Pass `patient_id=<dependent>`
    for just that dependent, or `include_family=true` for self + all
    dependents in one feed (useful for an 'all family' timeline view).
    """
    if patient_id is not None:
        target = await resolve_patient_context(patient_id, primary, db)
        where_ids = [target.id]
    elif include_family:
        where_ids = await _authorized_patient_ids(primary, db)
    else:
        where_ids = [primary.id]

    result = await db.execute(
        select(MedicalDocument)
        .where(MedicalDocument.patient_id.in_(where_ids))
        .order_by(MedicalDocument.created_at.desc())
    )
    docs = result.scalars().all()

    return DocumentListOut(
        documents=[DocumentOut.model_validate(d) for d in docs],
        total=len(docs),
    )


@router.get("/{document_id}", response_model=DocumentOut)
async def get_document(
    document_id: uuid.UUID,
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Get a specific document's details and extracted data.

    Works for any document owned by the primary OR any of their dependents.
    """
    doc = await _load_doc_for_primary(document_id, primary, db)
    return DocumentOut.model_validate(doc)


@router.delete("/{document_id}", status_code=status.HTTP_200_OK)
async def delete_document(
    document_id: uuid.UUID,
    background_tasks: BackgroundTasks,
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Delete a document, its Azure blob, and rebuild the patient summary.

    Works for any document owned by the primary OR any of their dependents.
    After deletion the patient's medical_summary is rebuilt from scratch
    (full rebuild, not incremental) so the deleted document's data is
    no longer reflected.
    """
    doc = await _load_doc_for_primary(document_id, primary, db)
    patient_id = doc.patient_id
    blob_path = doc.file_path

    # Clean up related rows that reference this document. These tables
    # (document_chat_messages, patient_upcoming_events) use a non-cascading
    # FK to medical_documents.id, so we must drop them explicitly before
    # deleting the document itself — otherwise Postgres raises a FK
    # violation and the whole request 500s.
    await db.execute(
        sql_delete(DocumentChatMessage).where(
            DocumentChatMessage.document_id == document_id
        )
    )
    await db.execute(
        sql_delete(PatientUpcomingEvent).where(
            PatientUpcomingEvent.source_document_id == document_id
        )
    )

    # Delete DB row so concurrent reads won't find it
    await db.delete(doc)
    await db.commit()

    # Delete Azure blob (best-effort — if it fails the row is already gone)
    if settings.USE_AZURE_STORAGE and blob_path and blob_path.startswith("patients/"):
        try:
            await azure_storage_service.delete_blob(blob_path)
        except Exception:
            pass  # Blob may already be gone or not yet uploaded

    # Rebuild patient summary without the deleted document
    async def _rebuild_summary_after_delete():
        from app.core.database import async_session
        async with async_session() as sess:
            await _update_patient_summary(patient_id, sess, new_doc_data=None)
            await sess.commit()

    background_tasks.add_task(_rebuild_summary_after_delete)

    return {"detail": "Document deleted", "document_id": str(document_id)}


@router.post("/summary/rebuild", response_model=PatientHealthSummary)
async def rebuild_health_summary(
    patient_id: uuid.UUID | None = Query(None),
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Force a full rebuild of the medical summary from all documents.

    Optionally targets a specific dependent via ?patient_id=.
    Useful if the incremental summary has drifted or after deleting docs.
    """
    target = await resolve_patient_context(patient_id or primary.id, primary, db)
    await _update_patient_summary(target.id, db, new_doc_data=None)
    await db.commit()
    return await get_health_summary(patient_id=target.id, primary=primary, db=db)


@router.get("/summary/health", response_model=PatientHealthSummary)
async def get_health_summary(
    patient_id: uuid.UUID | None = Query(
        None, description="Optional dependent id. Omit for primary."
    ),
    primary: Patient = Depends(get_current_patient),
    db: AsyncSession = Depends(get_db),
):
    """Get aggregated health summary across all uploaded documents."""
    target = await resolve_patient_context(patient_id or primary.id, primary, db)

    result = await db.execute(
        select(MedicalDocument).where(
            MedicalDocument.patient_id == target.id,
            MedicalDocument.processing_status == "completed",
        ).order_by(MedicalDocument.created_at.desc())
    )
    docs = result.scalars().all()

    all_meds = []
    all_diagnoses = []
    all_allergies = []
    all_vitals = []
    all_labs = []
    all_patient_summaries = []

    # Track the "worst" overall status across all docs
    status_priority = {"critical": 3, "attention_needed": 2, "all_good": 1}
    worst_status = None
    worst_status_message = None
    worst_priority = 0

    for doc in docs:
        data = doc.extracted_data or {}
        all_diagnoses.extend(data.get("diagnoses", []))
        all_allergies.extend(data.get("allergies_mentioned", []))

        # Enrich each medication with the document's date so the client
        # can determine active vs expired status
        doc_date_str = doc.document_date  # Already a "YYYY-MM-DD" string or None
        if not doc_date_str and doc.created_at:
            doc_date_str = doc.created_at.strftime("%Y-%m-%d")
        for med in data.get("medications", []):
            if isinstance(med, dict) and doc_date_str:
                med["prescribed_date"] = doc_date_str
            all_meds.append(med)

        # Enrich vitals and lab results with the document date
        for vital in data.get("vitals", []):
            if isinstance(vital, dict) and doc_date_str:
                vital["report_date"] = doc_date_str
            all_vitals.append(vital)
        for lab in data.get("lab_results", []):
            if isinstance(lab, dict) and doc_date_str:
                lab["report_date"] = doc_date_str
            all_labs.append(lab)

        # Collect patient summaries from each document
        ps = data.get("patient_summary")
        if ps:
            all_patient_summaries.append(ps)

        # Track worst overall status
        doc_status = data.get("overall_status")
        if doc_status:
            p = status_priority.get(doc_status, 0)
            if p > worst_priority:
                worst_priority = p
                worst_status = doc_status
                worst_status_message = data.get("overall_status_message")

    # Prefer the AGGREGATED patient summary (rolling narrative merged across all
    # documents on every upload, mirrors target.medical_summary for doctors).
    # Fall back to the most recent document's patient_summary for legacy patients
    # whose aggregated summary hasn't been regenerated yet — POST /documents/summary/rebuild
    # will populate it on next run.
    patient_summary = target.patient_summary or (
        all_patient_summaries[0] if all_patient_summaries else None
    )

    return PatientHealthSummary(
        patient_id=target.id,
        total_documents=len(docs),
        medications=all_meds,
        diagnoses=list(set(all_diagnoses)),
        allergies=list(set(all_allergies)),
        vitals=all_vitals,
        lab_results=all_labs,
        overall_summary=target.medical_summary,
        patient_summary=patient_summary,
        overall_status=worst_status,
        overall_status_message=worst_status_message,
        last_updated=docs[0].created_at if docs else None,
    )
