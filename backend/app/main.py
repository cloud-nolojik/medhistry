"""MedHistry API — FastAPI application entry point."""

from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse

from sqlalchemy import select, text

from app.core.config import settings
from app.core.database import engine, Base, async_session
from app.core.logging_middleware import RequestResponseLogger
from app.core.security import hash_password
from app.models.super_admin import SuperAdmin
from app.models.otp import OTP  # noqa: F401 — ensures create_all picks up the table
from app.models.patient_upcoming_event import PatientUpcomingEvent  # noqa: F401 — registers table
from app.models.document_chat_message import DocumentChatMessage  # noqa: F401 — registers table
from app.api.patients import router as patients_router
from app.api.qr import router as qr_router
from app.api.hospitals import router as hospitals_router
from app.api.doctors import router as doctors_router
from app.api.invitations import router as invitations_router
from app.api.super_admin import router as super_admin_router
from app.api.documents import router as documents_router
from app.api.upcoming_events import router as upcoming_events_router
from app.api.document_chat import router as document_chat_router


SEED_ADMIN_EMAIL = "admin@medhistry.com"
SEED_ADMIN_PASSWORD = "medhistry_2026"
SEED_ADMIN_NAME = "MedHistry Admin"


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Create database tables on startup and seed default admin."""
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
        # Migrations for existing tables (columns added after initial create_all)
        await conn.execute(text(
            "ALTER TABLE invitations ADD COLUMN IF NOT EXISTS doctor_email VARCHAR(200)"
        ))
        await conn.execute(text(
            "ALTER TABLE patients ADD COLUMN IF NOT EXISTS pin_hash VARCHAR(128)"
        ))
        await conn.execute(text(
            "ALTER TABLE patients ADD COLUMN IF NOT EXISTS is_phone_verified BOOLEAN DEFAULT FALSE"
        ))
        await conn.execute(text(
            "ALTER TABLE doctors ADD COLUMN IF NOT EXISTS pin_hash VARCHAR(128)"
        ))
        await conn.execute(text(
            "ALTER TABLE doctors ADD COLUMN IF NOT EXISTS pin_failed_attempts INTEGER NOT NULL DEFAULT 0"
        ))
        await conn.execute(text(
            "ALTER TABLE patients ADD COLUMN IF NOT EXISTS patient_summary TEXT"
        ))

    # Seed default super admin if not exists
    async with async_session() as session:
        result = await session.execute(
            select(SuperAdmin).where(SuperAdmin.email == SEED_ADMIN_EMAIL)
        )
        if not result.scalar_one_or_none():
            admin = SuperAdmin(
                email=SEED_ADMIN_EMAIL,
                name=SEED_ADMIN_NAME,
                password_hash=hash_password(SEED_ADMIN_PASSWORD),
            )
            session.add(admin)
            await session.commit()
            print(f"\n✓ Seeded default admin: {SEED_ADMIN_EMAIL} / {SEED_ADMIN_PASSWORD}\n")
        else:
            print(f"\n✓ Admin already exists: {SEED_ADMIN_EMAIL}\n")

    # Ensure Azure container exists (idempotent — safe to call repeatedly)
    if settings.USE_AZURE_STORAGE:
        from app.services.azure_storage_service import ensure_container_exists
        try:
            ensure_container_exists()
            print(f"\n✓ Azure container '{settings.AZURE_STORAGE_CONTAINER}' ready\n")
        except Exception as e:
            print(f"\n⚠ Azure container check failed: {e}\n")

    yield
    await engine.dispose()


app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    lifespan=lifespan,
)

# Request/Response logging (added first so it wraps everything)
app.add_middleware(RequestResponseLogger)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Routes
app.include_router(patients_router, prefix="/api/v1")
app.include_router(qr_router, prefix="/api/v1")
app.include_router(hospitals_router, prefix="/api/v1")
app.include_router(doctors_router, prefix="/api/v1")
app.include_router(invitations_router, prefix="/api/v1")
app.include_router(super_admin_router, prefix="/api/v1")
app.include_router(documents_router, prefix="/api/v1")
app.include_router(upcoming_events_router, prefix="/api/v1")
app.include_router(document_chat_router, prefix="/api/v1")


@app.get("/health")
async def health():
    return {"status": "ok", "service": settings.APP_NAME, "version": settings.APP_VERSION}


@app.get("/super-admin/login", response_class=HTMLResponse)
async def admin_panel():
    """Serve the unified MedHistry admin dashboard."""
    html_path = Path(__file__).parent / "super_admin_panel.html"
    return HTMLResponse(html_path.read_text())


@app.get("/hospital-admin/login", response_class=HTMLResponse)
async def hospital_admin_panel():
    """Serve the per-hospital admin dashboard (doctor invites)."""
    html_path = Path(__file__).parent / "hospital_admin_panel.html"
    return HTMLResponse(html_path.read_text())
