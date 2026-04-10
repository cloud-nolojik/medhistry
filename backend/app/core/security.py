"""Security utilities: password hashing, JWT tokens, QR token encryption."""

import time
import secrets
import json
from datetime import datetime, timedelta, timezone

from jose import jwt, JWTError
from passlib.context import CryptContext
from cryptography.fernet import Fernet
import hashlib
import base64

from app.core.config import settings

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


# --- Password Hashing ---

def hash_password(password: str) -> str:
    return pwd_context.hash(password)


def verify_password(plain: str, hashed: str) -> bool:
    return pwd_context.verify(plain, hashed)


# --- JWT Access Tokens ---

def create_access_token(subject: str, expires_delta: timedelta | None = None) -> str:
    expire = datetime.now(timezone.utc) + (
        expires_delta or timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    )
    return jwt.encode(
        {"sub": subject, "exp": expire},
        settings.SECRET_KEY,
        algorithm=settings.ALGORITHM,
    )


def decode_access_token(token: str) -> str | None:
    """Returns the subject (user ID) or None if invalid."""
    try:
        payload = jwt.decode(token, settings.SECRET_KEY, algorithms=[settings.ALGORITHM])
        return payload.get("sub")
    except JWTError:
        return None


def decode_token(token: str) -> dict | None:
    """Returns the full JWT payload dict, or None if invalid/expired."""
    try:
        return jwt.decode(token, settings.SECRET_KEY, algorithms=[settings.ALGORITHM])
    except JWTError:
        return None


# --- QR Token Encryption ---

def _get_fernet() -> Fernet:
    """Derive a Fernet key from the QR_TOKEN_SECRET."""
    key = hashlib.sha256(settings.QR_TOKEN_SECRET.encode()).digest()
    return Fernet(base64.urlsafe_b64encode(key))


def create_qr_token(patient_id: str, session_id: str) -> str:
    """Create an encrypted, time-limited QR token.

    The token encodes: patient_id, session_id, issued_at, nonce.
    This is what gets embedded in the QR code. No health data.
    """
    payload = json.dumps({
        "pid": patient_id,
        "sid": session_id,
        "iat": int(time.time()),
        "nonce": secrets.token_hex(8),
    })
    return _get_fernet().encrypt(payload.encode()).decode()


def verify_qr_token(token: str) -> dict | None:
    """Decrypt and validate a QR token. Returns payload or None.

    Checks that the token hasn't expired (QR_TOKEN_EXPIRY_SECONDS).
    """
    try:
        payload = json.loads(_get_fernet().decrypt(token.encode()))
        issued_at = payload.get("iat", 0)
        if time.time() - issued_at > settings.QR_TOKEN_EXPIRY_SECONDS:
            return None  # Token expired
        return payload
    except Exception:
        return None
