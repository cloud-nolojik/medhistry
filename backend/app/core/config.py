"""Application configuration loaded from environment variables."""

from pydantic_settings import BaseSettings
from typing import List


class Settings(BaseSettings):
    # App
    APP_NAME: str = "MedHistry API"
    APP_VERSION: str = "0.1.0"
    DEBUG: bool = False

    # Database
    DATABASE_URL: str = "postgresql+asyncpg://medhistry:medhistry@localhost:5432/medhistry"

    # Auth & Security
    SECRET_KEY: str = "change-me"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24  # 24 hours

    # QR Token
    QR_TOKEN_SECRET: str = "change-me-qr"
    QR_TOKEN_EXPIRY_SECONDS: int = 60
    SESSION_EXPIRY_MINUTES: int = 15

    # ===== AI Pipeline =====
    # Pluggable provider setup. Default: Gemini for both extraction and summary.
    # MedGemma 1.5 (medgemma_service.py) is on standby — switch to it later by
    # setting EXTRACTION_PROVIDER=medgemma without any code changes.
    EXTRACTION_PROVIDER: str = "gemini"  # "gemini" | "medgemma"

    # Gemini API — keys come from .env, never hardcoded
    GEMINI_API_KEY: str = ""
    GEMINI_MODEL: str = "gemini-2.5-flash"           # used for summary writing (fast, cheap)
    GEMINI_EXTRACTION_MODEL: str = "gemini-2.5-pro"  # used for document extraction (more accurate)

    # MedGemma on Vertex AI — STANDBY (not used unless EXTRACTION_PROVIDER=medgemma)
    VERTEX_PROJECT_ID: str = ""
    VERTEX_LOCATION: str = "asia-south1"
    MEDGEMMA_ENDPOINT_ID: str = ""  # Vertex AI endpoint ID where MedGemma is deployed
    MEDGEMMA_MODEL_VERSION: str = "google/medgemma-1.5-4b-it"
    GOOGLE_APPLICATION_CREDENTIALS: str = ""  # Path to service account JSON

    # File uploads (legacy local-disk fallback; primary storage is Azure Blob)
    UPLOAD_DIR: str = "uploads"
    MAX_FILE_SIZE_MB: int = 20

    # ===== Azure Blob Storage =====
    # Connection string lives in .env. Mobile clients NEVER see this — they
    # request short-lived SAS URLs from POST /documents/upload-url.
    AZURE_STORAGE_CONNECTION_STRING: str = ""
    AZURE_STORAGE_ACCOUNT_NAME: str = ""        # parsed from connection string if blank
    AZURE_STORAGE_CONTAINER: str = "medhistry-documents"
    AZURE_SAS_UPLOAD_TTL_MINUTES: int = 5       # write SAS lifetime
    AZURE_SAS_READ_TTL_MINUTES: int = 15        # read SAS lifetime (for view-original)
    USE_AZURE_STORAGE: bool = True              # set False to fall back to local disk in tests

    # ===== Resend (Email) =====
    RESEND_API_KEY: str = ""
    RESEND_FROM_EMAIL: str = "MedHistry <onboarding@resend.dev>"

    # CORS
    CORS_ORIGINS: List[str] = ["http://localhost:3000", "http://localhost:8081"]

    model_config = {"env_file": ".env", "extra": "ignore"}


settings = Settings()
