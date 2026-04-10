"""Azure Blob Storage service for medical document storage.

Architecture
------------
Mobile clients NEVER hold the Azure connection string. The flow is:

  1. Mobile calls POST /documents/upload-url with metadata (hospital_id, doctor_id, ext).
  2. Backend computes the blob path and mints a short-lived (5 min) write SAS URL,
     scoped to that single blob, and returns it along with a placeholder document_id.
  3. Mobile PUTs the file bytes directly to Azure using that SAS URL — backend never
     sees the file bytes on the upload path.
  4. Mobile calls POST /documents/confirm to flip the DB row from `pending_upload`
     to `pending` and trigger background extraction.
  5. Background task downloads the blob via the connection-string client, base64s it,
     and sends it to Gemini.
  6. To view the original, GET /documents/{id}/file returns a short-lived read SAS URL
     and the mobile loads the file directly from Azure.

Folder layout (inside the container)
------------------------------------
    patients/{patient_id}/hospitals/{hospital_id}/doctors/{doctor_id}/{document_id}.{ext}

`hospital_id` defaults to "self" for patient self-uploads. `doctor_id` defaults to "self".

Region note
-----------
Provision the storage account in `centralindia` or `southindia` for DPDPA data
residency, matching the Vertex AI `asia-south1` setup used for MedGemma.
"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Optional

from app.core.config import settings


# ───────────────────────── lazy SDK import ─────────────────────────
# We import the Azure SDK lazily so test environments without azure-storage-blob
# installed (USE_AZURE_STORAGE=false) don't blow up at module load time.

def _get_blob_service_client():
    from azure.storage.blob import BlobServiceClient

    if not settings.AZURE_STORAGE_CONNECTION_STRING:
        raise RuntimeError(
            "AZURE_STORAGE_CONNECTION_STRING is not set. "
            "Configure it in backend/.env to use Azure Blob Storage."
        )
    return BlobServiceClient.from_connection_string(settings.AZURE_STORAGE_CONNECTION_STRING)


def _parse_account_name() -> str:
    """Get the storage account name from settings, parsing the connection string if needed."""
    if settings.AZURE_STORAGE_ACCOUNT_NAME:
        return settings.AZURE_STORAGE_ACCOUNT_NAME
    cs = settings.AZURE_STORAGE_CONNECTION_STRING or ""
    for part in cs.split(";"):
        if part.startswith("AccountName="):
            return part.split("=", 1)[1]
    raise RuntimeError("Could not determine Azure storage account name.")


def _parse_account_key() -> str:
    cs = settings.AZURE_STORAGE_CONNECTION_STRING or ""
    for part in cs.split(";"):
        if part.startswith("AccountKey="):
            return part.split("=", 1)[1]
    raise RuntimeError("Could not determine Azure storage account key from connection string.")


# ───────────────────────── path builder ─────────────────────────

def build_blob_path(
    patient_id: str,
    document_id: str,
    file_ext: str,
    hospital_id: Optional[str] = None,
    doctor_id: Optional[str] = None,
) -> str:
    """Build the canonical blob path for a medical document.

    Layout: patients/{patient_id}/hospitals/{hospital_id|self}/doctors/{doctor_id|self}/{document_id}.{ext}
    """
    hospital = hospital_id or "self"
    doctor = doctor_id or "self"
    ext = file_ext.lstrip(".")
    return f"patients/{patient_id}/hospitals/{hospital}/doctors/{doctor}/{document_id}.{ext}"


# ───────────────────────── SAS URL minting ─────────────────────────

def generate_upload_sas(blob_path: str, content_type: str) -> str:
    """Mint a short-lived write-only SAS URL scoped to a single blob.

    The mobile client uses this URL with HTTP PUT (`x-ms-blob-type: BlockBlob`)
    to upload the file directly to Azure. The SAS expires after
    AZURE_SAS_UPLOAD_TTL_MINUTES (default 5 minutes).
    """
    from azure.storage.blob import BlobSasPermissions, generate_blob_sas

    account_name = _parse_account_name()
    account_key = _parse_account_key()
    container = settings.AZURE_STORAGE_CONTAINER
    expiry = datetime.now(timezone.utc) + timedelta(minutes=settings.AZURE_SAS_UPLOAD_TTL_MINUTES)

    sas = generate_blob_sas(
        account_name=account_name,
        container_name=container,
        blob_name=blob_path,
        account_key=account_key,
        permission=BlobSasPermissions(create=True, write=True),
        expiry=expiry,
        content_type=content_type,
    )
    return f"https://{account_name}.blob.core.windows.net/{container}/{blob_path}?{sas}"


def generate_read_sas(blob_path: str) -> str:
    """Mint a short-lived read-only SAS URL for viewing/downloading a blob.

    Used by GET /documents/{id}/file so the patient or doctor can view the
    original prescription directly from Azure (backend bandwidth not used).
    Expires after AZURE_SAS_READ_TTL_MINUTES (default 15 minutes).
    """
    from azure.storage.blob import BlobSasPermissions, generate_blob_sas

    account_name = _parse_account_name()
    account_key = _parse_account_key()
    container = settings.AZURE_STORAGE_CONTAINER
    expiry = datetime.now(timezone.utc) + timedelta(minutes=settings.AZURE_SAS_READ_TTL_MINUTES)

    sas = generate_blob_sas(
        account_name=account_name,
        container_name=container,
        blob_name=blob_path,
        account_key=account_key,
        permission=BlobSasPermissions(read=True),
        expiry=expiry,
    )
    return f"https://{account_name}.blob.core.windows.net/{container}/{blob_path}?{sas}"


# ───────────────────────── blob download ─────────────────────────

async def download_blob_bytes(blob_path: str) -> bytes:
    """Download a blob's bytes for backend processing (Gemini extraction).

    Runs the blocking Azure SDK call in a thread so it doesn't stall the event loop.
    """
    import asyncio

    def _sync_download() -> bytes:
        client = _get_blob_service_client()
        blob_client = client.get_blob_client(
            container=settings.AZURE_STORAGE_CONTAINER, blob=blob_path
        )
        downloader = blob_client.download_blob()
        return downloader.readall()

    return await asyncio.to_thread(_sync_download)


# ───────────────────────── blob deletion ─────────────────────────

async def delete_blob(blob_path: str) -> None:
    """Delete a blob from Azure. Runs in a thread to avoid blocking."""
    import asyncio

    def _sync_delete() -> None:
        client = _get_blob_service_client()
        blob_client = client.get_blob_client(
            container=settings.AZURE_STORAGE_CONTAINER, blob=blob_path
        )
        blob_client.delete_blob()

    await asyncio.to_thread(_sync_delete)


# ───────────────────────── container bootstrap ─────────────────────────

def ensure_container_exists() -> None:
    """Create the container on first use (idempotent).

    Call this once at startup. Safe to call repeatedly — Azure returns 409 which
    we swallow.
    """
    try:
        client = _get_blob_service_client()
        try:
            client.create_container(settings.AZURE_STORAGE_CONTAINER)
        except Exception:
            # Container already exists or insufficient perms — ignore
            pass
    except RuntimeError:
        # Connection string not configured — caller will surface this
        pass
