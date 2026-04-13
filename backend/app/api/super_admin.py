"""MedHistry Super Admin endpoints.

Super admins can:
  - Log in to the platform admin panel
  - Create and manage hospitals
  - Go inside any hospital and invite doctors on their behalf
  - List all hospitals and their doctors
"""

from datetime import datetime, timedelta, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import hash_password, verify_password, create_access_token
from app.models.super_admin import SuperAdmin
from app.models.hospital import Hospital
from app.models.doctor import Doctor
from app.models.patient import Patient
from app.models.invitation import Invitation, InvitationStatus
from app.schemas.super_admin import (
    SuperAdminCreate, SuperAdminLogin, SuperAdminOut,
    SuperAdminTokenResponse, HospitalCreate, HospitalUpdate,
)
from app.schemas.hospital import HospitalOut
from app.schemas.doctor import DoctorOut
from app.schemas.invitation import InvitationCreate, InvitationUpdate, InvitationOut
from app.api.deps import get_current_super_admin

router = APIRouter(prefix="/super-admin", tags=["super-admin"])

INVITATION_EXPIRY_DAYS = 7


# --- Auth ---

@router.post("/register", response_model=SuperAdminTokenResponse, status_code=status.HTTP_201_CREATED)
async def register_super_admin(data: SuperAdminCreate, db: AsyncSession = Depends(get_db)):
    """Register a MedHistry super admin. In production, seed this or protect behind a setup key."""
    existing = await db.execute(select(SuperAdmin).where(SuperAdmin.email == data.email.lower()))
    if existing.scalar_one_or_none():
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Email already registered")

    admin = SuperAdmin(
        email=data.email.lower(),
        name=data.name,
        password_hash=hash_password(data.password),
    )
    db.add(admin)
    await db.commit()
    await db.refresh(admin)

    token = create_access_token(f"superadmin:{admin.id}")
    return SuperAdminTokenResponse(access_token=token, admin=SuperAdminOut.model_validate(admin))


@router.post("/login", response_model=SuperAdminTokenResponse)
async def login_super_admin(data: SuperAdminLogin, db: AsyncSession = Depends(get_db)):
    """Super admin login with email + password."""
    result = await db.execute(select(SuperAdmin).where(SuperAdmin.email == data.email.lower()))
    admin = result.scalar_one_or_none()
    if not admin or not verify_password(data.password, admin.password_hash):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid email or password")

    token = create_access_token(f"superadmin:{admin.id}")
    return SuperAdminTokenResponse(access_token=token, admin=SuperAdminOut.model_validate(admin))


# --- Hospital Management ---

@router.post("/hospitals", response_model=HospitalOut, status_code=status.HTTP_201_CREATED)
async def create_hospital(
    data: HospitalCreate,
    admin: SuperAdmin = Depends(get_current_super_admin),
    db: AsyncSession = Depends(get_db),
):
    """Super admin creates a new hospital with admin credentials."""
    existing = await db.execute(select(Hospital).where(Hospital.slug == data.slug.lower()))
    if existing.scalar_one_or_none():
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Hospital slug already exists")

    hospital = Hospital(
        name=data.name,
        slug=data.slug.lower(),
        admin_password_hash=hash_password(data.admin_password),
        address=data.address,
        city=data.city,
        state=data.state,
        phone=data.phone,
        email=data.email,
    )
    db.add(hospital)
    await db.commit()
    await db.refresh(hospital)
    return HospitalOut.model_validate(hospital)


@router.get("/hospitals", response_model=list[HospitalOut])
async def list_hospitals(
    admin: SuperAdmin = Depends(get_current_super_admin),
    db: AsyncSession = Depends(get_db),
):
    """List all hospitals on the platform."""
    result = await db.execute(select(Hospital).order_by(Hospital.created_at.desc()))
    return [HospitalOut.model_validate(h) for h in result.scalars().all()]


@router.get("/hospitals/{hospital_id}", response_model=HospitalOut)
async def get_hospital(
    hospital_id: UUID,
    admin: SuperAdmin = Depends(get_current_super_admin),
    db: AsyncSession = Depends(get_db),
):
    """Get a specific hospital's details."""
    result = await db.execute(select(Hospital).where(Hospital.id == hospital_id))
    hospital = result.scalar_one_or_none()
    if not hospital:
        raise HTTPException(status_code=404, detail="Hospital not found")
    return HospitalOut.model_validate(hospital)


@router.put("/hospitals/{hospital_id}", response_model=HospitalOut)
async def update_hospital(
    hospital_id: UUID,
    data: HospitalUpdate,
    admin: SuperAdmin = Depends(get_current_super_admin),
    db: AsyncSession = Depends(get_db),
):
    """Super admin updates a hospital's details."""
    result = await db.execute(select(Hospital).where(Hospital.id == hospital_id))
    hospital = result.scalar_one_or_none()
    if not hospital:
        raise HTTPException(status_code=404, detail="Hospital not found")

    update_fields = data.model_dump(exclude_unset=True)
    # Handle password separately
    new_password = update_fields.pop("admin_password", None)
    if new_password:
        hospital.admin_password_hash = hash_password(new_password)

    for field, value in update_fields.items():
        setattr(hospital, field, value)

    await db.commit()
    await db.refresh(hospital)
    return HospitalOut.model_validate(hospital)


@router.get("/hospitals/{hospital_id}/doctors", response_model=list[DoctorOut])
async def list_hospital_doctors(
    hospital_id: UUID,
    admin: SuperAdmin = Depends(get_current_super_admin),
    db: AsyncSession = Depends(get_db),
):
    """List all doctors belonging to a hospital."""
    result = await db.execute(
        select(Doctor).where(Doctor.hospital_id == hospital_id).order_by(Doctor.created_at.desc())
    )
    return [DoctorOut.model_validate(d) for d in result.scalars().all()]


# --- Super Admin can invite doctors into any hospital ---

@router.post("/hospitals/{hospital_id}/invitations", response_model=InvitationOut, status_code=status.HTTP_201_CREATED)
async def create_invitation_for_hospital(
    hospital_id: UUID,
    data: InvitationCreate,
    admin: SuperAdmin = Depends(get_current_super_admin),
    db: AsyncSession = Depends(get_db),
):
    """Super admin invites a doctor into a specific hospital."""
    from app.services.email_service import send_invitation_email

    # Verify hospital exists
    hosp_result = await db.execute(select(Hospital).where(Hospital.id == hospital_id))
    hospital = hosp_result.scalar_one_or_none()
    if not hospital:
        raise HTTPException(status_code=404, detail="Hospital not found")

    # Check for existing pending invite
    existing = await db.execute(
        select(Invitation).where(
            Invitation.hospital_id == hospital_id,
            Invitation.doctor_phone == data.doctor_phone,
            Invitation.status == InvitationStatus.PENDING,
        )
    )
    if existing.scalar_one_or_none():
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Pending invitation already exists for this phone")

    invitation = Invitation(
        hospital_id=hospital_id,
        doctor_phone=data.doctor_phone,
        doctor_name=data.doctor_name,
        doctor_email=data.doctor_email,
        specialisation=data.specialisation,
        expires_at=datetime.now(timezone.utc) + timedelta(days=INVITATION_EXPIRY_DAYS),
    )
    db.add(invitation)
    await db.commit()
    await db.refresh(invitation)

    # Send invitation email if doctor email was provided
    email_sent = False
    if data.doctor_email:
        result = await send_invitation_email(
            to_email=data.doctor_email,
            doctor_name=data.doctor_name,
            hospital_name=hospital.name,
            invite_code=invitation.invite_code,
        )
        email_sent = result is not None

    return InvitationOut.model_validate(invitation)


@router.post("/invitations/{invitation_id}/resend-email")
async def resend_invitation_email(
    invitation_id: UUID,
    admin: SuperAdmin = Depends(get_current_super_admin),
    db: AsyncSession = Depends(get_db),
):
    """Resend the invitation email for an existing invitation."""
    from app.services.email_service import send_invitation_email

    result = await db.execute(select(Invitation).where(Invitation.id == invitation_id))
    invitation = result.scalar_one_or_none()
    if not invitation:
        raise HTTPException(status_code=404, detail="Invitation not found")
    if not invitation.doctor_email:
        raise HTTPException(status_code=400, detail="No email address on this invitation")

    # Get hospital name for the email
    hosp_result = await db.execute(select(Hospital).where(Hospital.id == invitation.hospital_id))
    hospital = hosp_result.scalar_one_or_none()

    email_result = await send_invitation_email(
        to_email=invitation.doctor_email,
        doctor_name=invitation.doctor_name,
        hospital_name=hospital.name if hospital else "Unknown Hospital",
        invite_code=invitation.invite_code,
    )
    if email_result:
        return {"status": "sent", "to": invitation.doctor_email}
    raise HTTPException(status_code=500, detail="Failed to send email — check server logs")


@router.put("/invitations/{invitation_id}", response_model=InvitationOut)
async def update_invitation(
    invitation_id: UUID,
    data: InvitationUpdate,
    admin: SuperAdmin = Depends(get_current_super_admin),
    db: AsyncSession = Depends(get_db),
):
    """Super admin edits an invitation's details (name, phone, email, specialisation)."""
    result = await db.execute(select(Invitation).where(Invitation.id == invitation_id))
    invitation = result.scalar_one_or_none()
    if not invitation:
        raise HTTPException(status_code=404, detail="Invitation not found")

    update_fields = data.model_dump(exclude_unset=True)
    for field, value in update_fields.items():
        setattr(invitation, field, value)

    await db.commit()
    await db.refresh(invitation)
    return InvitationOut.model_validate(invitation)


@router.delete("/invitations/{invitation_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_invitation(
    invitation_id: UUID,
    admin: SuperAdmin = Depends(get_current_super_admin),
    db: AsyncSession = Depends(get_db),
):
    """Super admin revokes/deletes a pending invitation."""
    result = await db.execute(
        select(Invitation).where(
            Invitation.id == invitation_id,
            Invitation.status == InvitationStatus.PENDING,
        )
    )
    invitation = result.scalar_one_or_none()
    if not invitation:
        raise HTTPException(status_code=404, detail="Invitation not found or already used")

    invitation.status = InvitationStatus.REVOKED
    await db.commit()


@router.get("/hospitals/{hospital_id}/invitations", response_model=list[InvitationOut])
async def list_invitations_for_hospital(
    hospital_id: UUID,
    admin: SuperAdmin = Depends(get_current_super_admin),
    db: AsyncSession = Depends(get_db),
):
    """Super admin views all invitations for a hospital."""
    result = await db.execute(
        select(Invitation).where(Invitation.hospital_id == hospital_id).order_by(Invitation.created_at.desc())
    )
    return [InvitationOut.model_validate(inv) for inv in result.scalars().all()]


# --- Dashboard Stats ---

@router.get("/stats")
async def platform_stats(
    admin: SuperAdmin = Depends(get_current_super_admin),
    db: AsyncSession = Depends(get_db),
):
    """Platform-wide stats for the super admin dashboard."""
    hospitals = (await db.execute(select(func.count()).select_from(Hospital))).scalar() or 0
    doctors = (await db.execute(select(func.count()).select_from(Doctor))).scalar() or 0
    patients = (await db.execute(select(func.count()).select_from(Patient))).scalar() or 0
    pending_invites = (await db.execute(
        select(func.count()).select_from(Invitation).where(Invitation.status == InvitationStatus.PENDING)
    )).scalar() or 0

    return {
        "hospitals": hospitals,
        "doctors": doctors,
        "patients": patients,
        "pending_invitations": pending_invites,
    }


# ============================================================
# AI Playground & Re-extraction (super admin only)
# ============================================================

from fastapi import UploadFile, File, Form
from app.core.config import settings
from app.services import playground_service
from app.schemas.playground import (
    PlaygroundResult,
    ReextractRequest,
    ReextractResult,
)


@router.get("/patients")
async def list_all_patients(
    admin: SuperAdmin = Depends(get_current_super_admin),
    db: AsyncSession = Depends(get_db),
):
    """Lightweight patient list for the Re-extract picker (id + name + phone +
    document count). Super-admin only."""
    from app.models.medical_document import MedicalDocument

    rows = await db.execute(
        select(
            Patient.id, Patient.name, Patient.phone, Patient.created_at,
            func.count(MedicalDocument.id).label("doc_count"),
        )
        .outerjoin(MedicalDocument, MedicalDocument.patient_id == Patient.id)
        .group_by(Patient.id)
        .order_by(Patient.created_at.desc())
    )
    return [
        {
            "id": str(r.id),
            "name": r.name,
            "phone": r.phone,
            "document_count": r.doc_count or 0,
            "created_at": r.created_at.isoformat() if r.created_at else None,
        }
        for r in rows
    ]


@router.get("/ai/providers")
async def list_ai_providers(admin: SuperAdmin = Depends(get_current_super_admin)):
    """List the AI providers and current default models so the playground UI
    knows what's selectable."""
    return {
        "current_extraction_provider": settings.EXTRACTION_PROVIDER,
        "providers": [
            {
                "id": "gemini",
                "label": "Gemini (Google AI)",
                "default_extraction_model": settings.GEMINI_EXTRACTION_MODEL,
                "default_summary_model": settings.GEMINI_MODEL,
                "available_models": [
                    "gemini-2.5-pro",
                    "gemini-2.5-flash",
                    "gemini-2.5-flash-lite",
                    "gemini-1.5-pro",
                    "gemini-1.5-flash",
                ],
                "configured": bool(settings.GEMINI_API_KEY),
            },
            {
                "id": "medgemma",
                "label": "MedGemma 1.5 (Vertex AI)",
                "default_extraction_model": settings.MEDGEMMA_MODEL_VERSION,
                "default_summary_model": None,  # MedGemma is extraction-only
                "available_models": [settings.MEDGEMMA_MODEL_VERSION or "google/medgemma-1.5-4b-it"],
                "configured": bool(settings.VERTEX_PROJECT_ID and settings.MEDGEMMA_ENDPOINT_ID),
            },
        ],
    }


@router.post("/ai/playground/run", response_model=PlaygroundResult)
async def playground_run(
    file: UploadFile = File(...),
    provider: str = Form("gemini"),
    extraction_model: str | None = Form(None),
    summary_model: str | None = Form(None),
    admin: SuperAdmin = Depends(get_current_super_admin),
):
    """Try a model combination on a sample document, no DB writes.

    The super admin uploads a sample report (PDF/JPG/PNG), picks a provider
    and optionally specific extraction/summary model names, and gets back
    the extracted JSON, the per-doc summary, AND a freshly generated
    "patient briefing" summary that mirrors what would land in the
    patient's medical_summary field. This makes A/B comparing models trivial.
    """
    allowed = {"application/pdf", "image/jpeg", "image/png", "image/jpg"}
    type_map = {"application/pdf": "pdf", "image/jpeg": "jpg", "image/png": "png", "image/jpg": "jpg"}
    if file.content_type not in allowed:
        raise HTTPException(status_code=400, detail=f"Unsupported file type: {file.content_type}")

    file_bytes = await file.read()
    if len(file_bytes) > settings.MAX_FILE_SIZE_MB * 1024 * 1024:
        raise HTTPException(status_code=413, detail="File too large")

    file_type = type_map[file.content_type]
    provider_lc = provider.lower()
    if provider_lc not in {"gemini", "medgemma"}:
        raise HTTPException(status_code=400, detail=f"Unknown provider: {provider}")

    try:
        result = await playground_service.run_playground(
            file_bytes=file_bytes,
            file_type=file_type,
            provider=provider_lc,
            extraction_model=extraction_model,
            summary_model=summary_model,
        )
    except RuntimeError as e:
        # Provider not configured (no API key, no Vertex endpoint, etc.)
        raise HTTPException(status_code=503, detail=str(e))

    return PlaygroundResult(**result)


@router.post("/ai/reextract", response_model=ReextractResult)
async def reextract_documents(
    payload: ReextractRequest,
    admin: SuperAdmin = Depends(get_current_super_admin),
    db: AsyncSession = Depends(get_db),
):
    """Re-run extraction on existing documents under a chosen provider/model.

    Use cases:
      - We switched the default model and want to backfill historical data.
      - We changed the extraction prompt and want to refresh everything.
      - We want to try a new model on one patient before rolling it out.

    Filters can be combined: `patient_id`, `document_ids`, both, or neither
    (= re-extract everything). `dry_run=true` computes the diffs without
    writing — use it to preview before committing.
    """
    provider_lc = payload.provider.lower()
    if provider_lc not in {"gemini", "medgemma"}:
        raise HTTPException(status_code=400, detail=f"Unknown provider: {payload.provider}")

    try:
        result = await playground_service.reextract_batch(
            db=db,
            provider=provider_lc,
            extraction_model=payload.extraction_model,
            patient_id=payload.patient_id,
            patient_ids=payload.patient_ids,
            document_ids=payload.document_ids,
            rebuild_patient_summaries=payload.rebuild_patient_summaries,
            dry_run=payload.dry_run,
        )
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))

    return ReextractResult(**result)
