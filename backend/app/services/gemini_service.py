"""Gemini AI service for medical document processing.

This service handles BOTH:
  1. Document extraction (PDF/image → structured medical JSON)
     Uses GEMINI_EXTRACTION_MODEL (default: gemini-2.5-pro for higher accuracy on
     structured extraction tasks).
  2. Patient summary generation (full rebuild + cost-efficient incremental merge)
     Uses GEMINI_MODEL (default: gemini-2.5-flash for fast, cheap summarization).

MedGemma 1.5 is implemented in medgemma_service.py and kept on standby — it can
be enabled later by setting EXTRACTION_PROVIDER=medgemma in .env, without code changes.

Requires GEMINI_API_KEY to be set in the environment.
"""

import json
import logging
import base64
import httpx
from pathlib import Path

logger = logging.getLogger(__name__)

from app.core.config import settings

GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models"


def _parse_json_lenient(text: str) -> dict:
    """Parse JSON from Gemini, handling common quirks like trailing commas
    and code-fenced output."""
    import re

    # Strip code fences if present (```json ... ```)
    stripped = text.strip()
    if stripped.startswith("```"):
        stripped = re.sub(r"^```(?:json)?\s*", "", stripped)
        stripped = re.sub(r"\s*```$", "", stripped)

    # First try strict parse
    try:
        return json.loads(stripped)
    except json.JSONDecodeError:
        pass

    # Remove trailing commas before } or ] (common Gemini quirk)
    cleaned = re.sub(r",\s*([}\]])", r"\1", stripped)
    try:
        return json.loads(cleaned)
    except json.JSONDecodeError:
        pass

    # Last resort: find the outermost { ... } and try that
    brace_start = stripped.find("{")
    brace_end = stripped.rfind("}")
    if brace_start != -1 and brace_end > brace_start:
        subset = stripped[brace_start:brace_end + 1]
        cleaned_subset = re.sub(r",\s*([}\]])", r"\1", subset)
        try:
            return json.loads(cleaned_subset)
        except json.JSONDecodeError as e:
            raise json.JSONDecodeError(
                f"Could not parse Gemini JSON even after cleanup: {e.msg}",
                e.doc, e.pos
            ) from e

    raise json.JSONDecodeError("No JSON object found in Gemini response", stripped, 0)

# Structured prompt that tells Gemini exactly what to extract from medical documents.
# Type-aware: the model first classifies the document, then extracts fields relevant
# to that type with extra depth (e.g. medication purpose/timing for prescriptions,
# patient explanations for lab results).
MEDICAL_EXTRACTION_PROMPT = """You are a medical document analyzer for MedHistry, a healthcare app used in Indian hospitals.

STEP 1: Identify the document type (doc_type).
STEP 2: Extract ALL relevant medical information into structured JSON, going DEEP on the fields that matter most for this document type.

Return ONLY valid JSON with this structure (include all fields, use null for missing data):

{
  "doc_type": "prescription" | "lab_report" | "discharge_summary" | "imaging_report" | "insurance" | "other",
  "document_date": "YYYY-MM-DD or null",
  "hospital_name": "string or null",
  "doctor_name": "string or null",
  "doctor_specialisation": "string or null",

  "diagnoses": ["list of diagnosis strings"],
  "symptoms": ["list of symptoms mentioned"],

  "medications": [
    {
      "name": "drug name (use generic name when possible, e.g. 'Pantoprazole' not just a brand name)",
      "brand_name": "brand name if different from generic, e.g. 'Pan-D', or null",
      "dosage": "e.g. 500mg, 2 tsp — NEVER null for prescriptions, read carefully",
      "frequency": "e.g. twice daily, once daily at night",
      "duration": "e.g. 7 days, 1 month, ongoing — null only if truly not mentioned",
      "instructions": "e.g. after meals, before food, with water",
      "time_of_day": "morning" | "afternoon" | "night" | "morning_and_night" | "morning_afternoon_night" | "as_needed" | "unknown",
      "food_instruction": "before_food" | "after_food" | "with_food" | "empty_stomach" | "any_time" | "unknown",
      "purpose": "A simple patient-friendly explanation of what this medicine is for, e.g. 'Stomach acid protection', 'Blood pressure control', 'Pain relief', 'Iron supplement for anemia', 'Antibiotic for infection'. ALWAYS provide this — infer from the drug name and context even if not stated on the document.",
      "category": "antibiotic" | "painkiller" | "antacid" | "vitamin_supplement" | "blood_pressure" | "diabetes" | "antihistamine" | "steroid" | "hormone" | "other"
    }
  ],

  "lab_results": [
    {
      "test_name": "e.g. HbA1c",
      "value": "6.5",
      "unit": "%",
      "reference_range": "4.0-5.6",
      "status": "normal" | "high" | "low" | "critical",
      "patient_explanation": "A simple 1-sentence explanation for the patient, e.g. 'Your blood sugar control is good' or 'Your kidney function is within the healthy range'"
    }
  ],

  "vitals": [
    {
      "name": "e.g. Blood Pressure",
      "value": "120/80",
      "unit": "mmHg",
      "status": "normal" | "high" | "low" | "critical",
      "patient_explanation": "e.g. 'Your blood pressure is normal'"
    }
  ],

  "allergies_mentioned": ["list of any allergies noted"],
  "follow_up": "follow-up instructions or null",

  "patient_summary": "A warm, reassuring 3-4 sentence summary written FOR THE PATIENT in simple everyday language. Emphasize what is NORMAL and healthy first. For anything abnormal, explain what it means simply and suggest next steps like 'discuss with your doctor'. Avoid medical jargon. Use phrases like 'Your tests show...', 'The good news is...', 'One thing to keep an eye on...'. Be encouraging but honest.",

  "clinical_summary": "A concise 2-3 sentence clinical summary FOR A DOCTOR. Lead with abnormal/critical findings. Include specific values. Focus on what is medically actionable.",

  "overall_status": "all_good" | "attention_needed" | "critical",
  "overall_status_message": "A very short 1-line message for the patient: 'Everything looks great!' or 'Most results are fine, one needs attention' or 'Some results need prompt medical attention'"
}

=== TYPE-SPECIFIC INSTRUCTIONS ===

If doc_type is "prescription":
  - Medications are the MOST IMPORTANT part. Extract EVERY medicine, even if handwritten.
  - ALWAYS determine time_of_day from the frequency:
      OD/once daily in the morning → "morning"
      OD/once daily at night/HS → "night"
      BD/twice daily → "morning_and_night"
      TDS/thrice daily → "morning_afternoon_night"
      SOS/as needed → "as_needed"
  - ALWAYS determine food_instruction from instructions:
      "before meals/food", "empty stomach", "AC" → "before_food" or "empty_stomach"
      "after meals/food", "PC" → "after_food"
      "with food/meals" → "with_food"
      If not specified, infer from drug type (e.g. antacids → "before_food", most antibiotics → "after_food")
  - ALWAYS provide purpose — even if it requires medical knowledge to infer
  - dosage must NEVER be null for a prescription document
  - patient_summary should focus on: "Your doctor has prescribed X medicines. The most important ones are... Take them regularly as directed."

If doc_type is "lab_report":
  - Lab results are the MOST IMPORTANT part. Extract EVERY test value.
  - ALWAYS provide patient_explanation for each result
  - Flag abnormal values accurately — compare value against reference_range
  - patient_summary should lead with what is normal, then gently mention anything that needs attention
  - clinical_summary should lead with abnormal/critical values

If doc_type is "discharge_summary":
  - Extract BOTH the diagnosis/conditions AND any new medications prescribed at discharge
  - follow_up is critical — extract exact follow-up date and instructions
  - patient_summary should explain the hospital stay and what to do at home

General rules:
- For Indian prescriptions, recognize: BD=twice daily, TDS=thrice daily, OD=once daily, SOS=as needed, HS=at bedtime, AC=before food, PC=after food, stat=immediately
- If the document is handwritten, do your best to read it
- The patient_summary should never cause unnecessary panic — be warm and factual
- The clinical_summary should be efficient and clinically precise
- Return ONLY the JSON, no other text"""


async def extract_document(
    file_path: str,
    file_type: str,
    model_override: str | None = None,
) -> dict:
    """Extract structured medical data from a document using Gemini's native document understanding.

    Uses the higher-accuracy GEMINI_EXTRACTION_MODEL (Gemini 2.5 Pro by default)
    rather than Flash, since extraction errors are clinically dangerous and the
    cost difference is small for typical document volumes.

    Args:
        file_path: Path to the uploaded file (PDF, JPG, PNG)
        file_type: File extension (pdf, jpg, png)
        model_override: optional Gemini model name (e.g. 'gemini-2.5-flash') used
                        ONLY for this call — lets the playground try different
                        models without touching settings.

    Returns:
        dict with extracted_text, extracted_data, ai_summary, doc_type, metadata, error,
        plus `_meta` (provider + model used) for audit/playground display.
    """
    if not settings.GEMINI_API_KEY:
        raise RuntimeError(
            "GEMINI_API_KEY is not set. Configure it in .env to enable document extraction."
        )

    return await _gemini_extract_document(file_path, file_type, model_override)


async def _gemini_extract_document(
    file_path: str, file_type: str, model_override: str | None = None
) -> dict:
    """Extract document using Gemini API with native PDF/image understanding."""
    # Read file and base64 encode
    file_bytes = Path(file_path).read_bytes()
    file_b64 = base64.standard_b64encode(file_bytes).decode("utf-8")

    # Map file type to MIME
    mime_map = {
        "pdf": "application/pdf",
        "jpg": "image/jpeg",
        "jpeg": "image/jpeg",
        "png": "image/png",
    }
    mime_type = mime_map.get(file_type.lower(), f"image/{file_type.lower()}")

    # Build Gemini API request — uses the higher-accuracy extraction model (2.5 Pro by default)
    model_name = model_override or settings.GEMINI_EXTRACTION_MODEL
    url = f"{GEMINI_API_URL}/{model_name}:generateContent?key={settings.GEMINI_API_KEY}"

    payload = {
        "contents": [{
            "parts": [
                {
                    "inline_data": {
                        "mime_type": mime_type,
                        "data": file_b64,
                    }
                },
                {
                    "text": MEDICAL_EXTRACTION_PROMPT,
                }
            ]
        }],
        "generationConfig": {
            "temperature": 0.1,  # Low temperature for accurate extraction
            "topP": 0.8,
            "maxOutputTokens": 8192,
        }
    }

    async with httpx.AsyncClient(timeout=60.0) as client:
        response = await client.post(url, json=payload)
        response.raise_for_status()
        result = response.json()

    # Parse Gemini response
    try:
        text_content = result["candidates"][0]["content"]["parts"][0]["text"]
        logger.info("Gemini raw response length: %d chars", len(text_content))
        extracted_data = _parse_json_lenient(text_content)
    except (KeyError, IndexError, json.JSONDecodeError) as e:
        raw = str(result)[:2000]
        logger.error("Failed to parse Gemini response: %s\nRaw (truncated): %s", e, raw)
        return {
            "extracted_text": str(result),
            "extracted_data": None,
            "ai_summary": None,
            "doc_type": None,
            "document_date": None,
            "hospital_name": None,
            "doctor_name": None,
            "error": f"Failed to parse Gemini response: {e}",
        }

    return {
        "extracted_text": text_content,
        "extracted_data": extracted_data,
        "ai_summary": extracted_data.get("patient_summary") or extracted_data.get("summary"),
        "clinical_summary": extracted_data.get("clinical_summary"),
        "doc_type": extracted_data.get("doc_type"),
        "document_date": extracted_data.get("document_date"),
        "hospital_name": extracted_data.get("hospital_name"),
        "doctor_name": extracted_data.get("doctor_name"),
        "error": None,
        "_meta": {"provider": "gemini", "model": model_name},
    }


async def generate_aggregated_summary(
    documents_data: list[dict], model_override: str | None = None
) -> str:
    """Generate an overall patient summary from ALL document extractions (full rebuild).

    Used for: first document, manual rebuild, or fallback when incremental merge fails.
    """
    if not settings.GEMINI_API_KEY:
        raise RuntimeError("GEMINI_API_KEY is not set.")
    return await _gemini_aggregated_summary(documents_data, model_override)


async def generate_incremental_summary(
    existing_summary: str,
    new_doc_data: dict,
    total_doc_count: int,
    model_override: str | None = None,
) -> str:
    """Merge a single new document's data into the existing patient summary."""
    if not settings.GEMINI_API_KEY:
        raise RuntimeError("GEMINI_API_KEY is not set.")
    return await _gemini_incremental_summary(
        existing_summary, new_doc_data, total_doc_count, model_override
    )


async def _gemini_aggregated_summary(
    documents_data: list[dict], model_override: str | None = None
) -> str:
    """Use Gemini to create a unified patient summary from multiple documents."""
    prompt = f"""You are a medical AI assistant for MedHistry. A doctor has just scanned a patient's QR code
and needs a quick briefing. Below are the extracted data from all of the patient's uploaded medical documents,
ordered from oldest to newest by document date.

Create a concise, clinically useful summary (3-5 sentences) that a doctor can read in 30 seconds.
Focus on: active conditions, current medications, critical lab values, allergies, and anything the doctor
needs to know RIGHT NOW before seeing this patient.

IMPORTANT: When documents span different dates, the MOST RECENT values are the current state. Older values
provide context for trends (e.g. "HbA1c improved from 7.2% to 6.8% over 3 months"). Always reflect the
latest known state of the patient.

Documents data (oldest first):
{json.dumps(documents_data, indent=2)}

Return ONLY the summary text, no JSON or formatting."""

    model_name = model_override or settings.GEMINI_MODEL
    url = f"{GEMINI_API_URL}/{model_name}:generateContent?key={settings.GEMINI_API_KEY}"
    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0.2,
            # Gemini 2.5 Flash/Pro are thinking models: thinking tokens count toward
            # maxOutputTokens, so a low ceiling truncates the user-visible text
            # mid-sentence. Disable thinking for simple summary generation and give
            # a generous token ceiling.
            "maxOutputTokens": 2048,
            "thinkingConfig": {"thinkingBudget": 0},
        },
    }

    async with httpx.AsyncClient(timeout=30.0) as client:
        response = await client.post(url, json=payload)
        response.raise_for_status()
        result = response.json()

    return _extract_summary_text(result, context="aggregated")


def _extract_summary_text(result: dict, *, context: str) -> str:
    """Pull the text out of a Gemini generateContent response and warn on truncation.

    Gemini returns finishReason=MAX_TOKENS when it ran out of output budget mid-way
    (including thinking tokens on 2.5 models). If we see that, we log a warning so
    truncated summaries don't silently ship to doctors.
    """
    try:
        candidate = result["candidates"][0]
        finish_reason = candidate.get("finishReason")
        text = candidate["content"]["parts"][0]["text"]
        if finish_reason and finish_reason != "STOP":
            logger.warning(
                "Gemini %s summary finished with reason=%s (text len=%d). "
                "Consider raising maxOutputTokens or checking safety filters.",
                context,
                finish_reason,
                len(text or ""),
            )
        return text
    except (KeyError, IndexError):
        logger.exception("Failed to parse Gemini %s summary response: %s", context, result)
        if context == "incremental":
            raise ValueError("Failed to parse Gemini incremental summary response")
        return "Unable to generate summary. Please review individual documents."


async def _gemini_incremental_summary(
    existing_summary: str,
    new_doc_data: dict,
    total_doc_count: int,
    model_override: str | None = None,
) -> str:
    """Merge new document data into existing summary using Gemini."""
    # Extract document_date so the model can reason about timeline
    doc_date = new_doc_data.get("document_date", "unknown")

    prompt = f"""You are a medical AI assistant for MedHistry. A patient has uploaded a medical document
(document #{total_doc_count}, dated {doc_date}). Below is their EXISTING medical summary followed by the NEW document's extracted data.

Your job: merge the new information into a single updated summary that reflects the patient's CURRENT health state.

Rules:
- Keep the summary concise (3-5 sentences), readable in 30 seconds by a doctor
- TIMELINE MATTERS: The document date is {doc_date}. If this is an OLDER document than what's already in the summary, do NOT overwrite current lab values or medications with older ones. Instead, note historical context if clinically relevant (e.g. "HbA1c has improved from 7.2% (Jan 2024) to 6.8% (Mar 2025)")
- If the new document is MORE RECENT and has newer lab values for the same test, update them and note the trend
- If a medication was changed or stopped in a newer document, reflect that. If the document is older, only add it as historical context
- NEW diagnoses should be added; do NOT drop existing conditions unless a NEWER document explicitly says they are resolved
- Allergies are cumulative — never remove an allergy regardless of document date
- Prioritize: active conditions, current medications, critical/abnormal labs, allergies, recent changes

EXISTING SUMMARY:
{existing_summary}

NEW DOCUMENT DATA:
{json.dumps(new_doc_data, indent=2)}

Return ONLY the updated summary text, no JSON or formatting."""

    model_name = model_override or settings.GEMINI_MODEL
    url = f"{GEMINI_API_URL}/{model_name}:generateContent?key={settings.GEMINI_API_KEY}"
    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0.2,
            # See note in _gemini_aggregated_summary — disable thinking for plain
            # paragraph-writing tasks to prevent thinking tokens from starving the
            # output budget and truncating the summary mid-sentence.
            "maxOutputTokens": 2048,
            "thinkingConfig": {"thinkingBudget": 0},
        },
    }

    async with httpx.AsyncClient(timeout=30.0) as client:
        response = await client.post(url, json=payload)
        response.raise_for_status()
        result = response.json()

    return _extract_summary_text(result, context="incremental")


