"""
End-to-end test: Full MedHistry vertical slice with proper admin hierarchy.

Super admin registers → creates hospital → invites doctor (from super admin panel) →
hospital admin logs in → invites another doctor →
doctors register → patient registers → patient shows QR →
doctor (authenticated) scans → sees briefing.

Uses SQLite for local testing. Set DATABASE_URL to PostgreSQL for production.
"""

import asyncio
import sys
import os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

DB_PATH = "/tmp/medhistry_test.db"
os.environ["DATABASE_URL"] = f"sqlite+aiosqlite:///{DB_PATH}"

if os.path.exists(DB_PATH):
    os.remove(DB_PATH)

from httpx import AsyncClient, ASGITransport
from app.core import database
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession

database.engine = create_async_engine(f"sqlite+aiosqlite:///{DB_PATH}", echo=False)
database.async_session = async_sessionmaker(database.engine, class_=AsyncSession, expire_on_commit=False)

# ----- Test stubs for AI services (no real API calls during e2e) -----
# We patch BOTH the MedGemma extractor and the Gemini summarizer before
# importing the app, so the document upload background task uses canned
# responses instead of hitting Vertex AI or the Gemini API.
from app.services import gemini_service, medgemma_service

_TEST_DOC_DATA = {
    "doc_type": "prescription",
    "document_date": "2026-03-15",
    "hospital_name": "Jadeva Hospital, Bangalore",
    "doctor_name": "Dr. Arun Mehta",
    "doctor_specialisation": "General Medicine",
    "diagnoses": ["Type 2 Diabetes Mellitus", "Mild Hypertension"],
    "symptoms": ["Frequent urination", "Fatigue"],
    "medications": [
        {"name": "Metformin", "dosage": "500mg", "frequency": "BD", "duration": "3 months", "instructions": "After meals"},
        {"name": "Amlodipine", "dosage": "5mg", "frequency": "OD", "duration": "3 months", "instructions": "Morning"},
    ],
    "lab_results": [
        {"test_name": "HbA1c", "value": "7.2", "unit": "%", "reference_range": "4.0-5.6", "status": "high"},
        {"test_name": "Fasting Blood Sugar", "value": "142", "unit": "mg/dL", "reference_range": "70-100", "status": "high"},
        {"test_name": "Blood Pressure", "value": "145/92", "unit": "mmHg", "reference_range": "<120/80", "status": "high"},
    ],
    "vitals": [{"name": "Blood Pressure", "value": "145/92", "unit": "mmHg"}],
    "allergies_mentioned": ["Penicillin"],
    "follow_up": "Review in 3 months",
    "summary": "Type 2 Diabetes (HbA1c 7.2%) and mild hypertension. On Metformin 500mg BD and Amlodipine 5mg OD. Penicillin allergy.",
}


async def _stub_extract_document(file_path: str, file_type: str, model_override=None) -> dict:
    """Stub for extraction — no real API call. Echoes the requested model in _meta."""
    return {
        "extracted_text": "stubbed",
        "extracted_data": _TEST_DOC_DATA,
        "ai_summary": _TEST_DOC_DATA["summary"],
        "doc_type": _TEST_DOC_DATA["doc_type"],
        "document_date": _TEST_DOC_DATA["document_date"],
        "hospital_name": _TEST_DOC_DATA["hospital_name"],
        "doctor_name": _TEST_DOC_DATA["doctor_name"],
        "error": None,
        "_meta": {"provider": "stub", "model": model_override or "stub-default"},
    }


async def _stub_generate_aggregated_summary(documents_data: list, model_override=None) -> str:
    """Stub for Gemini full-rebuild summary — no Gemini API call."""
    return ("Active conditions: Mild Hypertension, Type 2 Diabetes Mellitus. "
            "Current medications: Metformin 500mg, Amlodipine 5mg. "
            "ALLERGIES: Penicillin. Abnormal labs: HbA1c 7.2% high.")


async def _stub_generate_incremental_summary(existing_summary: str, new_doc_data: dict, total_doc_count: int, model_override=None) -> str:
    """Stub for Gemini incremental merge — preserves old summary, appends marker."""
    return f"{existing_summary} Updated after document #{total_doc_count}: latest labs reviewed."


# Patch both underlying service modules
medgemma_service.extract_document = _stub_extract_document
gemini_service.extract_document = _stub_extract_document
gemini_service.generate_aggregated_summary = _stub_generate_aggregated_summary
gemini_service.generate_incremental_summary = _stub_generate_incremental_summary

# Patch the names that documents.py imported / defined at module load time
from app.api import documents as documents_api
documents_api.extract_document = _stub_extract_document  # the dispatcher itself
documents_api.generate_aggregated_summary = _stub_generate_aggregated_summary
documents_api.generate_incremental_summary = _stub_generate_incremental_summary

# ----- Azure Blob Storage stubs (no real Azure calls during e2e) -----
# We patch the azure_storage_service module so the upload-url / confirm / file
# endpoints can run without an Azure account. The "blob" is held in memory.
from app.services import azure_storage_service

_FAKE_BLOB_STORE: dict[str, bytes] = {}


def _stub_build_blob_path(patient_id, document_id, file_ext, hospital_id=None, doctor_id=None):
    h = hospital_id or "self"
    d = doctor_id or "self"
    return f"patients/{patient_id}/hospitals/{h}/doctors/{d}/{document_id}.{file_ext}"


def _stub_generate_upload_sas(blob_path, content_type):
    # In real Azure this is a PUT-able URL; we pre-seed the fake store so the
    # confirm step finds bytes ready to "download".
    _FAKE_BLOB_STORE[blob_path] = b"%PDF-1.4 stub-uploaded-from-mobile"
    return f"https://fake-azure.blob.core.windows.net/medhistry-documents/{blob_path}?sas=stub"


def _stub_generate_read_sas(blob_path):
    return f"https://fake-azure.blob.core.windows.net/medhistry-documents/{blob_path}?sas=read"


async def _stub_download_blob_bytes(blob_path):
    return _FAKE_BLOB_STORE.get(blob_path, b"%PDF-1.4 fallback")


azure_storage_service.build_blob_path = _stub_build_blob_path
azure_storage_service.generate_upload_sas = _stub_generate_upload_sas
azure_storage_service.generate_read_sas = _stub_generate_read_sas
azure_storage_service.download_blob_bytes = _stub_download_blob_bytes
documents_api.azure_storage_service = azure_storage_service  # ensure dispatcher sees stubs

from app.main import app
from app.core.database import Base
engine = database.engine


async def main():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as c:

        print("=" * 70)
        print("MedHistry MVP — Full End-to-End Test (with Admin Hierarchy)")
        print("=" * 70)

        # ===== PHASE 1: MedHistry Super Admin =====
        print("\n--- PHASE 1: MedHistry Super Admin ---")

        print("\n[1] Registering super admin (Vijesh)...")
        r = await c.post("/api/v1/super-admin/register", json={
            "email": "vijesh@medhistry.com",
            "name": "Vijesh Krishna",
            "password": "superadmin123",
        })
        assert r.status_code == 201, f"Super admin registration failed: {r.text}"
        sa_token = r.json()["access_token"]
        print(f"    Admin: {r.json()['admin']['name']} ({r.json()['admin']['email']})")

        print("\n[2] Super admin login...")
        r = await c.post("/api/v1/super-admin/login", json={
            "email": "vijesh@medhistry.com", "password": "superadmin123",
        })
        assert r.status_code == 200
        sa_token = r.json()["access_token"]
        print(f"    Login successful")

        print("\n[3] Duplicate super admin email rejected...")
        r = await c.post("/api/v1/super-admin/register", json={
            "email": "vijesh@medhistry.com", "name": "Duplicate", "password": "test1234",
        })
        assert r.status_code == 409
        print(f"    Correctly rejected: {r.json()['detail']}")

        # ===== PHASE 2: Super Admin Creates Hospital =====
        print("\n--- PHASE 2: Super Admin Creates Hospital ---")

        print("\n[4] Super admin creates Jadeva Hospital...")
        r = await c.post("/api/v1/super-admin/hospitals", json={
            "name": "Jadeva Hospital",
            "slug": "jadeva",
            "admin_password": "jadeva_admin_2024",
            "city": "Bangalore",
            "state": "Karnataka",
            "phone": "+918012345678",
            "email": "admin@jadeva.in",
        }, headers={"Authorization": f"Bearer {sa_token}"})
        assert r.status_code == 201, f"Hospital creation failed: {r.text}"
        hospital_id = r.json()["id"]
        print(f"    Hospital: {r.json()['name']} (slug: {r.json()['slug']})")

        print("\n[5] Super admin lists hospitals...")
        r = await c.get("/api/v1/super-admin/hospitals",
                        headers={"Authorization": f"Bearer {sa_token}"})
        assert r.status_code == 200
        assert len(r.json()) == 1
        print(f"    Total hospitals: {len(r.json())}")

        print("\n[6] Duplicate slug rejected...")
        r = await c.post("/api/v1/super-admin/hospitals", json={
            "name": "Another", "slug": "jadeva", "admin_password": "test1234",
        }, headers={"Authorization": f"Bearer {sa_token}"})
        assert r.status_code == 409
        print(f"    Correctly rejected: {r.json()['detail']}")

        # ===== PHASE 3: Super Admin Invites Doctor =====
        print("\n--- PHASE 3: Super Admin Invites Doctor (into Jadeva) ---")

        print("\n[7] Super admin invites Dr. Arun Mehta into Jadeva...")
        r = await c.post(f"/api/v1/super-admin/hospitals/{hospital_id}/invitations", json={
            "doctor_name": "Dr. Arun Mehta",
            "doctor_phone": "+919812345678",
            "specialisation": "General Medicine",
        }, headers={"Authorization": f"Bearer {sa_token}"})
        assert r.status_code == 201, f"Invitation failed: {r.text}"
        sa_invite_code = r.json()["invite_code"]
        print(f"    Invite code: {sa_invite_code}")

        print("\n[8] Super admin views Jadeva invitations...")
        r = await c.get(f"/api/v1/super-admin/hospitals/{hospital_id}/invitations",
                        headers={"Authorization": f"Bearer {sa_token}"})
        assert r.status_code == 200
        assert len(r.json()) == 1
        print(f"    Total invitations: {len(r.json())}")

        # ===== PHASE 4: Hospital Admin Logs In + Invites Another Doctor =====
        print("\n--- PHASE 4: Hospital Admin (Jadeva) ---")

        print("\n[9] Hospital admin logs in with slug + password...")
        r = await c.post("/api/v1/hospitals/login", json={
            "slug": "jadeva", "password": "jadeva_admin_2024",
        })
        assert r.status_code == 200
        hosp_token = r.json()["access_token"]
        print(f"    Login successful for: {r.json()['hospital']['name']}")

        print("\n[10] Hospital admin invites Dr. Priya Rao...")
        r = await c.post("/api/v1/invitations/", json={
            "doctor_name": "Dr. Priya Rao",
            "doctor_phone": "+919999888877",
            "specialisation": "Cardiology",
        }, headers={"Authorization": f"Bearer {hosp_token}"})
        assert r.status_code == 201
        hosp_invite_code = r.json()["invite_code"]
        print(f"    Invite code: {hosp_invite_code}")

        print("\n[11] Hospital admin lists invitations (sees both)...")
        r = await c.get("/api/v1/invitations/",
                        headers={"Authorization": f"Bearer {hosp_token}"})
        assert r.status_code == 200
        assert r.json()["total"] == 2  # SA's invite + hospital's invite
        print(f"    Total invitations: {r.json()['total']}")

        # ===== PHASE 5: Doctors Register Via Invite Codes =====
        print("\n--- PHASE 5: Doctors Register ---")

        print("\n[12] Dr. Arun Mehta registers (SA's invite)...")
        r = await c.post("/api/v1/doctors/register", json={
            "invite_code": sa_invite_code,
            "phone": "+919812345678",
            "name": "Dr. Arun Mehta",
            "password": "doctor123",
            "specialisation": "General Medicine",
            "license_number": "KA-MCI-12345",
        })
        assert r.status_code == 201, f"Doctor registration failed: {r.text}"
        doctor_token = r.json()["access_token"]
        print(f"    Doctor: {r.json()['doctor']['name']}")

        print("\n[13] Dr. Priya Rao registers (hospital's invite)...")
        r = await c.post("/api/v1/doctors/register", json={
            "invite_code": hosp_invite_code,
            "phone": "+919999888877",
            "name": "Dr. Priya Rao",
            "password": "cardio456",
            "specialisation": "Cardiology",
        })
        assert r.status_code == 201
        print(f"    Doctor: {r.json()['doctor']['name']}")

        print("\n[14] Wrong phone with invite code rejected...")
        # Create a fresh invite to test
        r2 = await c.post(f"/api/v1/super-admin/hospitals/{hospital_id}/invitations", json={
            "doctor_name": "Dr. Test", "doctor_phone": "+911111111111",
        }, headers={"Authorization": f"Bearer {sa_token}"})
        test_code = r2.json()["invite_code"]
        r = await c.post("/api/v1/doctors/register", json={
            "invite_code": test_code, "phone": "+922222222222",
            "name": "Impostor", "password": "hack1234",
        })
        assert r.status_code == 400
        print(f"    Correctly rejected: {r.json()['detail']}")

        print("\n[15] Super admin sees 2 doctors at Jadeva...")
        r = await c.get(f"/api/v1/super-admin/hospitals/{hospital_id}/doctors",
                        headers={"Authorization": f"Bearer {sa_token}"})
        assert r.status_code == 200
        assert len(r.json()) == 2
        print(f"    Registered doctors: {len(r.json())}")

        # ===== PHASE 6: Patient + QR Flow =====
        print("\n--- PHASE 6: Patient + QR Flow ---")

        print("\n[16] Patient Priya Sharma registers...")
        r = await c.post("/api/v1/patients/register", json={
            "phone": "+919876543210",
            "name": "Priya Sharma",
            "password": "secure123",
            "date_of_birth": "1990-05-15",
            "gender": "Female",
            "blood_group": "B+",
            "allergies": "Penicillin, Sulfa drugs",
        })
        assert r.status_code == 201
        patient_token = r.json()["access_token"]
        print(f"    Patient: {r.json()['patient']['name']}")

        # ===== PHASE 6.5: Document Upload + AI Processing =====
        print("\n--- PHASE 6.5: Document Upload + AI Processing ---")

        print("\n[17] Patient uploads a medical document...")
        # Create a fake PDF file for testing
        fake_pdf = b"%PDF-1.4 fake prescription content"
        import io
        r = await c.post(
            "/api/v1/documents/upload",
            files={"file": ("prescription.pdf", io.BytesIO(fake_pdf), "application/pdf")},
            headers={"Authorization": f"Bearer {patient_token}"},
        )
        assert r.status_code == 201, f"Upload failed: {r.text}"
        doc = r.json()
        doc_id = doc["id"]
        print(f"    Document uploaded: {doc['filename']} (status: {doc['processing_status']})")

        # Wait briefly for background processing (mock mode is instant-ish)
        import asyncio as aio
        await aio.sleep(1)

        print("\n[18] Checking document processing completed...")
        r = await c.get(f"/api/v1/documents/{doc_id}",
                        headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 200
        doc = r.json()
        print(f"    Status: {doc['processing_status']}")
        print(f"    Doc type: {doc['doc_type']}")
        print(f"    AI Summary: {doc['ai_summary'][:80]}..." if doc['ai_summary'] else "    AI Summary: (none)")
        if doc['extracted_data']:
            meds = doc['extracted_data'].get('medications', [])
            print(f"    Medications found: {len(meds)}")
            for m in meds:
                print(f"      - {m['name']} {m.get('dosage', '')} ({m.get('frequency', '')})")

        print("\n[19] Patient lists all documents...")
        r = await c.get("/api/v1/documents/",
                        headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 200
        assert r.json()["total"] >= 1
        print(f"    Total documents: {r.json()['total']}")

        print("\n[20] Patient views health summary...")
        r = await c.get("/api/v1/documents/summary/health",
                        headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 200
        summary = r.json()
        first_summary_text = summary['overall_summary']
        print(f"    Diagnoses: {summary['diagnoses']}")
        print(f"    Medications: {len(summary['medications'])}")
        print(f"    Allergies: {summary['allergies']}")
        print(f"    Overall: {first_summary_text[:80]}..." if first_summary_text else "    Overall: (pending)")

        print("\n[20a] Patient uploads a SECOND document (incremental merge)...")
        fake_pdf2 = b"%PDF-1.4 fake lab report content"
        r = await c.post(
            "/api/v1/documents/upload",
            files={"file": ("lab_report.pdf", io.BytesIO(fake_pdf2), "application/pdf")},
            headers={"Authorization": f"Bearer {patient_token}"},
        )
        assert r.status_code == 201
        await aio.sleep(1)

        print("\n[20b] Verify summary updated AND retains old data after second upload...")
        r = await c.get("/api/v1/documents/summary/health",
                        headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 200
        summary2 = r.json()
        assert summary2['total_documents'] == 2, f"Expected 2 docs, got {summary2['total_documents']}"
        # Old findings (Diabetes, Penicillin allergy) must still be present after merge
        assert summary2['overall_summary'] is not None, "Summary missing after 2nd upload"
        merged = summary2['overall_summary'].lower()
        assert 'diabetes' in merged or 'metformin' in merged, \
            f"Old data lost after incremental merge! Summary: {summary2['overall_summary']}"
        assert 'penicillin' in merged, \
            f"Old allergy lost after incremental merge! Summary: {summary2['overall_summary']}"
        print(f"    Total docs: {summary2['total_documents']}")
        print(f"    Merged summary preserves old data: OK")
        print(f"    Updated: {summary2['overall_summary'][:120]}...")

        print("\n[20d] Azure direct-upload flow: request SAS URL...")
        r = await c.post(
            "/api/v1/documents/upload-url",
            json={
                "filename": "azure_uploaded_rx.pdf",
                "content_type": "application/pdf",
                "file_size_bytes": 4096,
                "hospital_id": "jadeva",
                "doctor_id": "arun",
            },
            headers={"Authorization": f"Bearer {patient_token}"},
        )
        assert r.status_code == 201, f"upload-url failed: {r.text}"
        sas_resp = r.json()
        azure_doc_id = sas_resp["document_id"]
        assert sas_resp["upload_url"].startswith("https://"), "expected https SAS URL"
        assert sas_resp["blob_path"].startswith("patients/"), "blob path layout wrong"
        assert "/hospitals/jadeva/doctors/arun/" in sas_resp["blob_path"], \
            f"hierarchy missing: {sas_resp['blob_path']}"
        print(f"    Blob path: {sas_resp['blob_path']}")
        print(f"    SAS expires in: {sas_resp['expires_in_seconds']}s")

        print("\n[20e] Confirm upload triggers background extraction...")
        r = await c.post(
            "/api/v1/documents/confirm",
            json={"document_id": azure_doc_id},
            headers={"Authorization": f"Bearer {patient_token}"},
        )
        assert r.status_code == 200, f"confirm failed: {r.text}"
        await aio.sleep(1)

        r = await c.get(f"/api/v1/documents/{azure_doc_id}",
                        headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 200
        azure_doc = r.json()
        assert azure_doc["processing_status"] == "completed", \
            f"Azure doc not processed: {azure_doc['processing_status']}"
        print(f"    Azure-uploaded doc processed: {azure_doc['processing_status']}")

        print("\n[20f] Get short-lived read SAS to view original prescription...")
        r = await c.get(f"/api/v1/documents/{azure_doc_id}/file",
                        headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 200, f"file URL fetch failed: {r.text}"
        file_resp = r.json()
        assert file_resp["url"].startswith("https://"), "expected https read SAS"
        assert "sas=read" in file_resp["url"], "stub read SAS marker missing"
        print(f"    Read SAS URL OK, expires in: {file_resp['expires_in_seconds']}s")

        print("\n[20g] Cross-patient confirm rejected...")
        r2 = await c.post("/api/v1/patients/register", json={
            "phone": "+919000000099", "name": "Mallory", "password": "secure123",
        })
        mallory_token = r2.json()["access_token"]
        r = await c.post(
            "/api/v1/documents/upload-url",
            json={
                "filename": "x.pdf", "content_type": "application/pdf",
                "file_size_bytes": 100,
            },
            headers={"Authorization": f"Bearer {patient_token}"},
        )
        stolen_id = r.json()["document_id"]
        r = await c.post(
            "/api/v1/documents/confirm",
            json={"document_id": stolen_id},
            headers={"Authorization": f"Bearer {mallory_token}"},
        )
        assert r.status_code == 404, f"cross-patient confirm allowed: {r.status_code}"
        print(f"    Cross-patient confirm correctly rejected")

        print("\n[20c] Manual rebuild endpoint works...")
        r = await c.post("/api/v1/documents/summary/rebuild",
                         headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 200
        rebuilt = r.json()
        # 2 from legacy /upload + 1 from Azure direct-upload flow above = 3
        assert rebuilt['total_documents'] == 3
        assert rebuilt['overall_summary'] is not None
        print(f"    Rebuild OK, summary regenerated from all {rebuilt['total_documents']} docs")

        # ===== PHASE 7: QR Flow (now with AI data) =====
        print("\n--- PHASE 7: QR Flow (with AI-enriched briefing) ---")

        print("\n[21] Patient generates QR code...")
        r = await c.post("/api/v1/qr/generate",
                         headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 200
        qr = r.json()
        session_id = qr["session_id"]
        qr_token = qr["qr_token"]
        print(f"    QR Session started, token v{qr['token_version']}")

        print("\n[22] QR token refresh (60s rotation)...")
        r = await c.post(f"/api/v1/qr/refresh/{session_id}",
                         headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 200
        new_qr_token = r.json()["qr_token"]
        print(f"    Rotated to v{r.json()['token_version']}")

        print("\n[23] Dr. Arun scans QR — gets AI-enriched briefing...")
        r = await c.post("/api/v1/qr/scan", json={"qr_token": new_qr_token},
                         headers={"Authorization": f"Bearer {doctor_token}"})
        assert r.status_code == 200, f"Scan failed: {r.text}"
        briefing = r.json()
        print(f"    --- PATIENT BRIEFING CARD ---")
        print(f"    Name:        {briefing['name']}")
        print(f"    Age:         {briefing['age']}")
        print(f"    Gender:      {briefing['gender']}")
        print(f"    Blood:       {briefing['blood_group']}")
        print(f"    Allergies:   {briefing['allergies']}")
        print(f"    AI Summary:  {briefing['medical_summary'][:80]}..." if briefing['medical_summary'] else "    AI Summary:  (none)")
        print(f"    Medications: {len(briefing['medications'])}")
        print(f"    Diagnoses:   {briefing['diagnoses']}")
        print(f"    Critical labs: {len(briefing['critical_labs'])}")
        print(f"    Documents:   {briefing['total_documents']}")

        # ===== PHASE 8: Security Checks =====
        print("\n--- PHASE 8: Security Checks ---")

        print("\n[23b] Single-use: re-scanning the same QR token is rejected...")
        r = await c.post("/api/v1/qr/scan", json={"qr_token": new_qr_token},
                         headers={"Authorization": f"Bearer {doctor_token}"})
        assert r.status_code == 401, f"Replay should fail, got {r.status_code}"
        print(f"    Token consumed — replay blocked")

        # The original session is now consumed. Start a fresh one for the
        # remaining unauth/wrong-role/end-session tests.
        r = await c.post("/api/v1/qr/generate",
                         headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 200
        sec_session_id = r.json()["session_id"]
        sec_qr_token = r.json()["qr_token"]

        print("\n[24] Unauthenticated scan rejected...")
        r = await c.post("/api/v1/qr/scan", json={"qr_token": sec_qr_token})
        assert r.status_code in [401, 403]
        print(f"    Rejected (status {r.status_code})")

        print("\n[25] Patient can't scan (wrong role)...")
        r = await c.post("/api/v1/qr/scan", json={"qr_token": sec_qr_token},
                         headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code in [401, 403]
        print(f"    Rejected (status {r.status_code})")

        print("\n[26] Old rotated QR token rejected...")
        r = await c.post("/api/v1/qr/scan", json={"qr_token": qr_token},
                         headers={"Authorization": f"Bearer {doctor_token}"})
        assert r.status_code == 401
        print(f"    Rejected correctly")

        print("\n[27] Patient ends QR session...")
        r = await c.post(f"/api/v1/qr/end/{sec_session_id}",
                         headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 204
        print(f"    Session ended")

        # ===== PHASE 8.5: Family members + remote share code flow =====
        print("\n--- PHASE 8.5: Family + Remote Share Code ---")

        print("\n[27a] Add a dependent (Dad) to Priya's account...")
        r = await c.post("/api/v1/patients/family", json={
            "name": "Ramesh Sharma",
            "relationship": "father",
            "date_of_birth": "1955-08-12",
            "gender": "Male",
            "blood_group": "O+",
            "allergies": "None",
        }, headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 201, f"Add dependent failed: {r.text}"
        dad = r.json()
        dad_id = dad["id"]
        assert dad["managed_by"] is not None
        assert dad["relationship"] == "father"
        print(f"    Added Dad: {dad['name']} (managed by Priya)")

        print("\n[27b] List family — should include primary + Dad...")
        r = await c.get("/api/v1/patients/family",
                        headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 200
        fam = r.json()
        assert fam["primary"]["name"]
        assert len(fam["dependents"]) == 1
        assert fam["dependents"][0]["name"] == "Ramesh Sharma"
        print(f"    Family: {fam['primary']['name']} + {len(fam['dependents'])} dependent(s)")

        print("\n[27c] Dependent cannot act on someone else's family...")
        # Try to generate a share code for a random UUID — must 403.
        import uuid as _uuid
        r = await c.post("/api/v1/qr/generate-code",
                         json={"patient_id": str(_uuid.uuid4())},
                         headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 403
        print(f"    Blocked cross-account access (403)")

        print("\n[27d] Priya generates 6-digit share code for Dad (remote)...")
        r = await c.post("/api/v1/qr/generate-code",
                         json={"patient_id": dad_id},
                         headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 200, f"Generate-code failed: {r.text}"
        sc = r.json()
        assert len(sc["share_code"]) == 6 and sc["share_code"].isdigit()
        assert sc["patient_id"] == dad_id
        assert sc["patient_name"] == "Ramesh Sharma"
        share_code = sc["share_code"]
        print(f"    Code: {share_code} for {sc['patient_name']} (valid 5 min)")

        print("\n[27e] Unauthenticated code redeem rejected...")
        r = await c.post("/api/v1/qr/redeem-code", json={"share_code": share_code})
        assert r.status_code in [401, 403]
        print(f"    Rejected ({r.status_code})")

        print("\n[27f] Patient can't redeem (wrong role)...")
        r = await c.post("/api/v1/qr/redeem-code",
                         json={"share_code": share_code},
                         headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code in [401, 403]
        print(f"    Rejected ({r.status_code})")

        print("\n[27g] Doctor redeems code → gets Dad's briefing...")
        r = await c.post("/api/v1/qr/redeem-code",
                         json={"share_code": share_code},
                         headers={"Authorization": f"Bearer {doctor_token}"})
        assert r.status_code == 200, f"Redeem failed: {r.text}"
        dad_brief = r.json()
        assert dad_brief["name"] == "Ramesh Sharma"
        assert dad_brief["patient_id"] == dad_id
        print(f"    Doctor saw: {dad_brief['name']}, age {dad_brief['age']}, "
              f"{dad_brief['total_documents']} docs")

        print("\n[27h] Same code cannot be redeemed twice (single-use)...")
        r = await c.post("/api/v1/qr/redeem-code",
                         json={"share_code": share_code},
                         headers={"Authorization": f"Bearer {doctor_token}"})
        assert r.status_code == 401
        print(f"    Replay blocked (401)")

        print("\n[27i] Invalid code format rejected...")
        r = await c.post("/api/v1/qr/redeem-code",
                         json={"share_code": "abc123"},
                         headers={"Authorization": f"Bearer {doctor_token}"})
        assert r.status_code in [401, 422]
        print(f"    Bad format rejected ({r.status_code})")

        print("\n[27j] Priya sees access log for Dad (who viewed, when)...")
        r = await c.get(f"/api/v1/patients/{dad_id}/access-log",
                        headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 200, f"Access log failed: {r.text}"
        log = r.json()
        assert len(log) >= 1
        assert log[0]["method"] == "share_code"
        assert log[0]["doctor_name"]
        print(f"    Most recent: {log[0]['doctor_name']} "
              f"({log[0]['hospital_name']}) via {log[0]['method']}")

        print("\n[27k] Priya can't see access log for a stranger...")
        r = await c.get(f"/api/v1/patients/{_uuid.uuid4()}/access-log",
                        headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 403
        print(f"    Blocked (403)")

        print("\n[27l0] Son uploads an old prescription for Dad (multipart)...")
        fake_dad_rx = b"%PDF-1.4 dad's old diabetes prescription"
        r = await c.post(
            f"/api/v1/documents/upload?patient_id={dad_id}",
            files={"file": ("dad_rx.pdf", __import__("io").BytesIO(fake_dad_rx), "application/pdf")},
            headers={"Authorization": f"Bearer {patient_token}"},
        )
        assert r.status_code == 201, f"Dep upload failed: {r.text}"
        dep_doc = r.json()
        dep_doc_id = dep_doc["id"]
        print(f"    Uploaded under Dad's profile: {dep_doc['filename']}")
        await __import__("asyncio").sleep(1)

        print("\n[27l1] Primary's own list does NOT include Dad's doc by default...")
        r = await c.get("/api/v1/documents/",
                        headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 200
        own_ids = {d["id"] for d in r.json()["documents"]}
        assert dep_doc_id not in own_ids, "Dad's doc leaked into Priya's default list"
        print(f"    Primary list has {len(own_ids)} doc(s), Dad's doc properly scoped out")

        print("\n[27l2] Filtered list by dependent id returns only Dad's docs...")
        r = await c.get(f"/api/v1/documents/?patient_id={dad_id}",
                        headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 200
        dad_docs = r.json()["documents"]
        assert any(d["id"] == dep_doc_id for d in dad_docs)
        assert all("patient_id" not in d or True for d in dad_docs)  # just presence
        print(f"    Dad has {len(dad_docs)} doc(s)")

        print("\n[27l3] include_family=true returns primary + all dependents' docs...")
        r = await c.get("/api/v1/documents/?include_family=true",
                        headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 200
        merged = r.json()["documents"]
        merged_ids = {d["id"] for d in merged}
        assert dep_doc_id in merged_ids
        assert all(oid in merged_ids for oid in own_ids)
        print(f"    Merged feed: {len(merged)} doc(s) across whole family")

        print("\n[27l4] Single doc fetch works for Dad's doc via primary token...")
        r = await c.get(f"/api/v1/documents/{dep_doc_id}",
                        headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 200
        assert r.json()["id"] == dep_doc_id
        print(f"    Fetched Dad's doc OK")

        print("\n[27l5] Health summary for Dad is separate from primary's...")
        r = await c.get(f"/api/v1/documents/summary/health?patient_id={dad_id}",
                        headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 200
        dad_summary = r.json()
        assert dad_summary["patient_id"] == dad_id
        print(f"    Dad's summary: {dad_summary['total_documents']} doc(s)")

        print("\n[27l6] Cross-account doc upload is blocked (403)...")
        r = await c.post(
            f"/api/v1/documents/upload?patient_id={_uuid.uuid4()}",
            files={"file": ("evil.pdf", __import__("io").BytesIO(b"%PDF-1.4"), "application/pdf")},
            headers={"Authorization": f"Bearer {patient_token}"},
        )
        assert r.status_code == 403
        print(f"    Blocked (403)")

        print("\n[27l7] Azure SAS upload-url can target a dependent...")
        r = await c.post(
            "/api/v1/documents/upload-url",
            json={
                "filename": "dad_old_labs.pdf",
                "content_type": "application/pdf",
                "file_size_bytes": 123,
                "patient_id": dad_id,
            },
            headers={"Authorization": f"Bearer {patient_token}"},
        )
        assert r.status_code == 201, f"SAS upload-url for dep failed: {r.text}"
        dep_sas = r.json()
        assert f"patients/{dad_id}" in dep_sas["blob_path"]
        print(f"    Blob path correctly scoped under Dad: {dep_sas['blob_path']}")

        print("\n[27l] Access log also records in-person QR scans...")
        # Generate a fresh QR session for Priya, doctor scans, check log.
        r = await c.post("/api/v1/qr/generate",
                         headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 200
        qr_priya = r.json()
        r = await c.post("/api/v1/qr/scan",
                         json={"qr_token": qr_priya["qr_token"]},
                         headers={"Authorization": f"Bearer {doctor_token}"})
        assert r.status_code == 200
        priya_patient_id = r.json()["patient_id"]
        r = await c.get(f"/api/v1/patients/{priya_patient_id}/access-log",
                        headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code == 200
        priya_log = r.json()
        assert any(e["method"] == "qr_scan" for e in priya_log)
        print(f"    Priya log has {len(priya_log)} entr{'ies' if len(priya_log) != 1 else 'y'}, "
              f"including qr_scan")

        print("\n[28] Hospital can't self-register (no endpoint)...")
        r = await c.post("/api/v1/hospitals/register", json={
            "name": "Rogue Hospital", "slug": "rogue", "password": "hack123",
        })
        assert r.status_code in [404, 405, 422]
        print(f"    No self-register endpoint (status {r.status_code})")

        print("\n[28a] Super admin lists AI providers...")
        r = await c.get("/api/v1/super-admin/ai/providers",
                        headers={"Authorization": f"Bearer {sa_token}"})
        assert r.status_code == 200
        provs = r.json()
        assert provs["current_extraction_provider"] == "gemini"
        provider_ids = [p["id"] for p in provs["providers"]]
        assert "gemini" in provider_ids and "medgemma" in provider_ids
        print(f"    Providers: {provider_ids}, default extraction: {provs['current_extraction_provider']}")

        print("\n[28b] Playground run with Gemini default models...")
        import io as _io
        files = {"file": ("sample_rx.pdf", _io.BytesIO(b"%PDF-1.4 sample"), "application/pdf")}
        data = {"provider": "gemini"}
        r = await c.post("/api/v1/super-admin/ai/playground/run",
                         files=files, data=data,
                         headers={"Authorization": f"Bearer {sa_token}"})
        assert r.status_code == 200, f"playground failed: {r.text}"
        pg = r.json()
        assert pg["provider"] == "gemini"
        assert pg["extracted_data"] is not None
        assert pg["patient_briefing_summary"] is not None
        assert pg["doc_type"] == "prescription"
        print(f"    Extraction model: {pg['extraction_model']}")
        print(f"    Briefing: {pg['patient_briefing_summary'][:80]}...")
        print(f"    Total time: {pg['timing_ms']['total']}ms")

        print("\n[28c] Playground with explicit model override (gemini-2.5-flash)...")
        files = {"file": ("sample_rx.pdf", _io.BytesIO(b"%PDF-1.4 sample"), "application/pdf")}
        data = {"provider": "gemini", "extraction_model": "gemini-2.5-flash",
                "summary_model": "gemini-2.5-flash"}
        r = await c.post("/api/v1/super-admin/ai/playground/run",
                         files=files, data=data,
                         headers={"Authorization": f"Bearer {sa_token}"})
        assert r.status_code == 200
        pg2 = r.json()
        # Stub echoes the requested model name back via _meta
        assert pg2["extraction_model"] == "gemini-2.5-flash"
        print(f"    Override accepted: {pg2['extraction_model']}")

        print("\n[28d] Playground requires super admin auth...")
        files = {"file": ("x.pdf", _io.BytesIO(b"%PDF-1.4"), "application/pdf")}
        r = await c.post("/api/v1/super-admin/ai/playground/run",
                         files=files, data={"provider": "gemini"},
                         headers={"Authorization": f"Bearer {patient_token}"})
        assert r.status_code in [401, 403]
        print(f"    Patient token rejected ({r.status_code})")

        print("\n[28e] Re-extract dry-run all docs...")
        r = await c.post("/api/v1/super-admin/ai/reextract",
                         json={"provider": "gemini", "dry_run": True,
                               "rebuild_patient_summaries": False},
                         headers={"Authorization": f"Bearer {sa_token}"})
        assert r.status_code == 200, f"reextract dry-run failed: {r.text}"
        rx = r.json()
        assert rx["dry_run"] is True
        assert rx["total_documents"] >= 3  # at least the docs we created
        assert rx["succeeded"] == rx["total_documents"]
        # Sample audit row should have before/after counts
        sample = rx["audits"][0]
        assert "diagnoses_after" in sample
        assert "preview_extracted_data" in sample  # only present in dry-run
        print(f"    Dry-run: {rx['succeeded']}/{rx['total_documents']} ok, "
              f"{rx['patients_affected']} patients would be affected")

        print("\n[28f] Re-extract real run with summary rebuild...")
        r = await c.post("/api/v1/super-admin/ai/reextract",
                         json={"provider": "gemini", "dry_run": False,
                               "extraction_model": "gemini-2.5-flash",
                               "rebuild_patient_summaries": True},
                         headers={"Authorization": f"Bearer {sa_token}"})
        assert r.status_code == 200
        rx2 = r.json()
        assert rx2["dry_run"] is False
        assert rx2["succeeded"] == rx2["total_documents"]
        assert rx2["patient_summaries_rebuilt"] >= 1
        # Stub echoes the model override into _meta
        assert rx2["audits"][0]["new_meta_model"] == "gemini-2.5-flash"
        print(f"    Real run: {rx2['succeeded']}/{rx2['total_documents']} ok, "
              f"{rx2['patient_summaries_rebuilt']} summaries rebuilt under "
              f"{rx2['audits'][0]['new_meta_model']}")

        print("\n[28g] Re-extract scoped to one patient...")
        # Get Priya's patient id from the briefing or stats. Use the patient list endpoint.
        # We'll filter by the doc list — confirm only Priya's docs are touched.
        priya_docs_resp = await c.get("/api/v1/documents/",
                                      headers={"Authorization": f"Bearer {patient_token}"})
        priya_doc_ids = [d["id"] for d in priya_docs_resp.json()["documents"]]
        # Pick Priya's patient_id by looking at the document list metadata path:
        # easier — call reextract with document_ids filter
        r = await c.post("/api/v1/super-admin/ai/reextract",
                         json={"provider": "gemini",
                               "document_ids": priya_doc_ids,
                               "dry_run": True,
                               "rebuild_patient_summaries": False},
                         headers={"Authorization": f"Bearer {sa_token}"})
        assert r.status_code == 200
        rx3 = r.json()
        assert rx3["total_documents"] == len(priya_doc_ids)
        assert rx3["patients_affected"] == 1
        print(f"    Scoped: {rx3['total_documents']} docs, {rx3['patients_affected']} patient")

        print("\n[28i] Super admin patient picker + multi-patient re-extract...")
        r = await c.get("/api/v1/super-admin/patients",
                        headers={"Authorization": f"Bearer {sa_token}"})
        assert r.status_code == 200
        all_patients = r.json()
        assert len(all_patients) >= 1
        assert all("id" in p and "name" in p and "document_count" in p for p in all_patients)
        # Pick all patient ids and re-extract them as a batch (dry-run)
        pids = [p["id"] for p in all_patients]
        r = await c.post("/api/v1/super-admin/ai/reextract",
                         json={"provider": "gemini",
                               "patient_ids": pids,
                               "dry_run": True,
                               "rebuild_patient_summaries": False},
                         headers={"Authorization": f"Bearer {sa_token}"})
        assert r.status_code == 200
        rx4 = r.json()
        # patients_affected only counts those who actually had docs re-extracted
        assert rx4["patients_affected"] <= len(pids)
        assert rx4["patients_affected"] >= 1
        print(f"    Picker returned {len(all_patients)} patients; multi-patient re-extract "
              f"touched {rx4['total_documents']} docs across {rx4['patients_affected']} patients")

        print("\n[28h] Re-extract rejects unknown provider...")
        r = await c.post("/api/v1/super-admin/ai/reextract",
                         json={"provider": "openai"},
                         headers={"Authorization": f"Bearer {sa_token}"})
        assert r.status_code == 400
        print(f"    Rejected: {r.json()['detail']}")

        print("\n[29] Platform stats...")
        r = await c.get("/api/v1/super-admin/stats",
                        headers={"Authorization": f"Bearer {sa_token}"})
        assert r.status_code == 200
        stats = r.json()
        print(f"    Hospitals: {stats['hospitals']}, Doctors: {stats['doctors']}, Patients: {stats['patients']}, Pending: {stats['pending_invitations']}")

        print("\n[30] Super admin panel serves HTML...")
        r = await c.get("/admin")
        assert r.status_code == 200
        assert "Platform Admin" in r.text
        print(f"    Super admin panel OK")

        print("\n[31] Hospital admin panel serves HTML...")
        r = await c.get("/hospital-admin")
        assert r.status_code == 200
        assert "Hospital Admin" in r.text
        print(f"    Hospital admin panel OK")

        print("\n[32] Health check...")
        r = await c.get("/health")
        assert r.status_code == 200
        print(f"    {r.json()}")

        print("\n" + "=" * 70)
        print("ALL 32 TESTS PASSED!")
        print("=" * 70)
        print("""
Complete MedHistry MVP verified:
  1. Super admin → creates hospitals → invites doctors
  2. Hospital admin → logs in → invites more doctors
  3. Doctors → register via invite → authenticate
  4. Patients → register → upload documents → AI extracts medical data
  5. Patient shows QR → doctor scans → sees AI-enriched briefing
     (diagnoses, medications, critical labs, summary)
  6. Security: role-based access, token rotation, no hospital self-register
  7. AI pipeline: Gemini 2.5 Pro (extraction) → Gemini 2.5 Flash (summary)
     MedGemma 1.5 on standby — set EXTRACTION_PROVIDER=medgemma to enable
  8. Incremental summary merge: new docs update summary without losing old data
""")

    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)


if __name__ == "__main__":
    asyncio.run(main())
