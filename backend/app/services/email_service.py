"""Email service using Resend for sending invitation emails to doctors."""

import logging
import traceback
import resend

from app.core.config import settings

logger = logging.getLogger(__name__)


def _init_resend():
    """Set the Resend API key. Safe to call multiple times."""
    if settings.RESEND_API_KEY:
        resend.api_key = settings.RESEND_API_KEY


async def send_invitation_email(
    to_email: str,
    doctor_name: str,
    hospital_name: str,
    invite_code: str,
) -> dict | None:
    """Send a doctor invitation email via Resend.

    Returns the Resend response dict on success, or None if email is
    not configured or sending fails.
    """
    if not settings.RESEND_API_KEY:
        print(f"\n⚠️  RESEND_API_KEY not set — skipping invitation email to {to_email}\n")
        return None

    _init_resend()
    print(f"\n📧 Sending invitation email to {to_email} via Resend...")

    html_body = f"""
    <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;max-width:520px;margin:0 auto;padding:32px;background:#f8fafc">
      <div style="background:#0E2A3A;border-radius:12px;padding:32px;text-align:center;margin-bottom:24px">
        <h1 style="color:#50C8DC;margin:0;font-size:24px">MedHistry</h1>
        <p style="color:#94a3b8;margin:8px 0 0;font-size:14px">Doctor Invitation</p>
      </div>
      <div style="background:#fff;border-radius:12px;padding:24px;border:1px solid #e2e8f0">
        <p style="font-size:16px;color:#0E2A3A">Hello Dr. {doctor_name},</p>
        <p style="color:#334155;line-height:1.6">
          You have been invited to join <strong>{hospital_name}</strong> on MedHistry —
          a platform that helps doctors and patients manage medical records seamlessly.
        </p>
        <p style="color:#334155;line-height:1.6">Use the invitation code below to register in the <strong>MedHistry Pro</strong> app:</p>
        <div style="background:#f1f5f9;border:2px dashed #50C8DC;border-radius:8px;padding:16px;text-align:center;margin:20px 0">
          <span style="font-family:monospace;font-size:20px;font-weight:700;color:#0E2A3A;letter-spacing:1px">{invite_code}</span>
        </div>
        <p style="color:#64748B;font-size:13px">This code is valid for 7 days. If you did not expect this invitation, you can safely ignore this email.</p>
      </div>
      <p style="text-align:center;color:#94a3b8;font-size:12px;margin-top:20px">MedHistry &mdash; Simplifying healthcare records</p>
    </div>
    """

    try:
        params: resend.Emails.SendParams = {
            "from": settings.RESEND_FROM_EMAIL,
            "to": [to_email],
            "subject": f"You're invited to join {hospital_name} on MedHistry",
            "html": html_body,
        }
        result = resend.Emails.send(params)
        email_id = getattr(result, "id", None) or (result.get("id") if isinstance(result, dict) else str(result))
        print(f"✅ Invitation email sent to {to_email} — Resend ID: {email_id}\n")
        return {"id": email_id}
    except Exception as e:
        print(f"❌ Failed to send invitation email to {to_email}: {e}")
        traceback.print_exc()
        return None
