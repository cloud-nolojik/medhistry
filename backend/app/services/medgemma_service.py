"""MedGemma 1.5 4B service for medical document extraction.

MedGemma is Google's medical-domain Gemma 3 variant, specifically trained for
PDF→JSON extraction of medical lab reports, prescriptions, and EHR text. We host
it on a Vertex AI endpoint (preferably asia-south1 / Mumbai) so PHI never leaves
Indian infrastructure — important for DPDPA compliance and Jadeva Hospital.

This service handles ONLY document extraction. Summary writing is delegated to
Gemini 2.5 Flash (gemini_service.py) which is better at fluent doctor-facing prose.
"""

import json
import base64
import re
from pathlib import Path

import httpx

from app.core.config import settings

# MedGemma extraction prompt — leans on its medical-domain training
MEDGEMMA_EXTRACTION_PROMPT = """You are MedGemma, a medical document analyzer. Analyze this medical document and extract ALL relevant medical information into a structured JSON format.

Return ONLY valid JSON with this exact structure (use null for missing data):

{
  "doc_type": "prescription" | "lab_report" | "discharge_summary" | "imaging_report" | "insurance" | "other",
  "document_date": "YYYY-MM-DD or null",
  "hospital_name": "string or null",
  "doctor_name": "string or null",
  "doctor_specialisation": "string or null",
  "diagnoses": ["list of diagnosis strings"],
  "symptoms": ["list of symptoms mentioned"],
  "medications": [
    {"name": "drug name", "dosage": "e.g. 500mg", "frequency": "e.g. BD", "duration": "e.g. 7 days", "instructions": "e.g. after meals"}
  ],
  "lab_results": [
    {"test_name": "e.g. HbA1c", "value": "6.5", "unit": "%", "reference_range": "4.0-5.6", "status": "normal" | "high" | "low" | "critical"}
  ],
  "vitals": [
    {"name": "e.g. Blood Pressure", "value": "120/80", "unit": "mmHg"}
  ],
  "allergies_mentioned": ["list of any allergies noted"],
  "follow_ups": [
    {
      "kind": "repeat_test" | "appointment" | "medication_review" | "vaccination" | "procedure" | "lifestyle" | "other",
      "title": "short label",
      "due_on": "YYYY-MM-DD or null",
      "due_hint": "EXACT original phrase from the doc (e.g. 'in 3 months', 'after 15 days') — required when due_on is null",
      "with_whom": "doctor/specialist/department or null",
      "notes": "short extra context or null",
      "urgency": "routine" | "soon" | "urgent"
    }
  ],
  "summary": "A clear 2-3 sentence summary written for a doctor who needs this patient's history in 30 seconds. Focus on what is medically significant."
}

Important:
- Extract medication names in their generic form when possible
- Flag any lab values outside reference range
- For Indian prescriptions, recognize abbreviations (BD=twice daily, TDS=thrice daily, OD=once daily, SOS=as needed)
- For follow_ups: return [] if nothing mentioned; put every distinct future action as its own entry; prefer absolute dates, fall back to relative phrases copied verbatim into due_hint (e.g. "after 15 days", "review in 3 months") so the backend can anchor them against document_date
- Return ONLY the JSON object, no other text"""


async def _get_vertex_access_token() -> str:
    """Get a Google Cloud access token for Vertex AI calls.

    Uses the service account JSON pointed to by GOOGLE_APPLICATION_CREDENTIALS.
    In production on GCE/GKE the metadata server can also provide tokens automatically.
    """
    try:
        from google.auth import default
        from google.auth.transport.requests import Request
    except ImportError as e:
        raise RuntimeError(
            "google-auth library not installed. Run: pip install google-auth"
        ) from e

    credentials, _ = default(scopes=["https://www.googleapis.com/auth/cloud-platform"])
    credentials.refresh(Request())
    return credentials.token


async def extract_document(
    file_path: str,
    file_type: str,
    model_override: str | None = None,
) -> dict:
    """Extract structured medical data from a document using MedGemma 1.5 4B.

    Args:
        file_path: Path to the uploaded file (PDF, JPG, PNG)
        file_type: File extension (pdf, jpg, png)
        model_override: optional MedGemma model version label (advisory only —
                        the actual model is whatever is deployed to MEDGEMMA_ENDPOINT_ID).

    Returns:
        dict with extracted_text, extracted_data, ai_summary, doc_type, metadata, error,
        plus `_meta` (provider + model used) for audit/playground display.
    """
    if not settings.VERTEX_PROJECT_ID or not settings.MEDGEMMA_ENDPOINT_ID:
        raise RuntimeError(
            "MedGemma not configured. Set VERTEX_PROJECT_ID and MEDGEMMA_ENDPOINT_ID in .env."
        )

    # Read and base64 encode the file
    file_bytes = Path(file_path).read_bytes()
    file_b64 = base64.standard_b64encode(file_bytes).decode("utf-8")

    mime_map = {
        "pdf": "application/pdf",
        "jpg": "image/jpeg",
        "jpeg": "image/jpeg",
        "png": "image/png",
    }
    mime_type = mime_map.get(file_type.lower(), f"image/{file_type.lower()}")

    # Get access token
    access_token = await _get_vertex_access_token()

    # Vertex AI prediction endpoint URL
    url = (
        f"https://{settings.VERTEX_LOCATION}-aiplatform.googleapis.com/v1/"
        f"projects/{settings.VERTEX_PROJECT_ID}/locations/{settings.VERTEX_LOCATION}/"
        f"endpoints/{settings.MEDGEMMA_ENDPOINT_ID}:predict"
    )

    # MedGemma on Vertex follows the standard prediction format with multimodal instances
    payload = {
        "instances": [
            {
                "prompt": MEDGEMMA_EXTRACTION_PROMPT,
                "multi_modal_data": {
                    "image": {
                        "mime_type": mime_type,
                        "data": file_b64,
                    }
                },
                "max_tokens": 4096,
                "temperature": 0.1,
                "top_p": 0.8,
            }
        ]
    }

    headers = {
        "Authorization": f"Bearer {access_token}",
        "Content-Type": "application/json",
    }

    async with httpx.AsyncClient(timeout=120.0) as client:
        response = await client.post(url, json=payload, headers=headers)
        response.raise_for_status()
        result = response.json()

    # Parse Vertex prediction response
    try:
        # Vertex predictions return {"predictions": [...]}; the exact shape depends on
        # the serving container. The Hugging Face TGI container returns generated_text.
        prediction = result["predictions"][0]
        if isinstance(prediction, dict):
            text_content = prediction.get("generated_text") or prediction.get("text") or ""
        else:
            text_content = str(prediction)

        extracted_data = _parse_json_from_text(text_content)
    except (KeyError, IndexError, json.JSONDecodeError, ValueError) as e:
        return {
            "extracted_text": str(result),
            "extracted_data": None,
            "ai_summary": None,
            "doc_type": None,
            "document_date": None,
            "hospital_name": None,
            "doctor_name": None,
            "error": f"Failed to parse MedGemma response: {e}",
        }

    return {
        "extracted_text": text_content,
        "extracted_data": extracted_data,
        "ai_summary": extracted_data.get("summary"),
        "doc_type": extracted_data.get("doc_type"),
        "document_date": extracted_data.get("document_date"),
        "hospital_name": extracted_data.get("hospital_name"),
        "doctor_name": extracted_data.get("doctor_name"),
        "error": None,
        "_meta": {
            "provider": "medgemma",
            "model": model_override or settings.MEDGEMMA_MODEL_VERSION,
            "endpoint_id": settings.MEDGEMMA_ENDPOINT_ID,
        },
    }


def _parse_json_from_text(text: str) -> dict:
    """Extract a JSON object from MedGemma's text output.

    MedGemma is text-out only and may wrap JSON in code fences or include
    leading/trailing prose despite the prompt. Be lenient.
    """
    text = text.strip()

    # Strip markdown code fences if present
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text)

    # Try direct parse first
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    # Fall back: find the first { and matching } via brace counting
    start = text.find("{")
    if start == -1:
        raise ValueError("No JSON object found in MedGemma output")

    depth = 0
    for i in range(start, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return json.loads(text[start : i + 1])

    raise ValueError("Unbalanced braces in MedGemma output")
