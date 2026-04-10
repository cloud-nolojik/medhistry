"""CLI: re-run extraction on existing medical documents under a chosen model.

Usage examples:

    # Dry-run all docs under medgemma — see what would change, write nothing
    python scripts/reextract_documents.py --provider medgemma --dry-run

    # Real run for one patient under gemini-2.5-pro
    python scripts/reextract_documents.py --provider gemini --patient-id <UUID>

    # Real run for specific docs under a custom model
    python scripts/reextract_documents.py --provider gemini \
        --extraction-model gemini-2.5-flash \
        --document-ids <id1> <id2>

    # Re-extract everything and rebuild every affected patient's summary
    python scripts/reextract_documents.py --provider medgemma --rebuild-summaries

The script prints a per-document audit line and a final summary so it's easy
to grep / pipe into a log file.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import sys
import uuid
from pathlib import Path

# Make `app.*` importable when running from backend/
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.core.database import async_session  # noqa: E402
from app.services import playground_service  # noqa: E402


def _parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Re-extract MedHistry documents under a chosen AI model")
    p.add_argument("--provider", default="gemini", choices=["gemini", "medgemma"])
    p.add_argument("--extraction-model", default=None,
                   help="Specific model name (e.g. gemini-2.5-flash). Defaults to provider's configured model.")
    p.add_argument("--patient-id", default=None, help="Filter to a single patient UUID")
    p.add_argument("--patient-ids", nargs="*", default=None,
                   help="Filter to these patient UUIDs (space-separated, for 3-4 patient batches)")
    p.add_argument("--document-ids", nargs="*", default=None, help="Filter to these document UUIDs")
    p.add_argument("--rebuild-summaries", action="store_true",
                   help="After re-extracting, run a full summary rebuild for every affected patient")
    p.add_argument("--dry-run", action="store_true",
                   help="Compute new extractions but do not write to the database")
    p.add_argument("--json", action="store_true", help="Print full JSON audit instead of human summary")
    return p.parse_args()


async def _main(args: argparse.Namespace) -> int:
    patient_id = uuid.UUID(args.patient_id) if args.patient_id else None
    patient_ids = [uuid.UUID(p) for p in (args.patient_ids or [])] or None
    document_ids = [uuid.UUID(d) for d in (args.document_ids or [])] or None

    async with async_session() as db:
        result = await playground_service.reextract_batch(
            db=db,
            provider=args.provider,
            extraction_model=args.extraction_model,
            patient_id=patient_id,
            patient_ids=patient_ids,
            document_ids=document_ids,
            rebuild_patient_summaries=args.rebuild_summaries,
            dry_run=args.dry_run,
        )

    if args.json:
        print(json.dumps(result, indent=2, default=str))
        return 0 if result["failed"] == 0 else 1

    # Human-readable output
    print("=" * 70)
    print(f"Re-extraction run — provider={result['provider']} "
          f"model={result['extraction_model'] or '(default)'} "
          f"dry_run={result['dry_run']}")
    print("=" * 70)
    for a in result["audits"]:
        marker = "OK " if a["status"] == "ok" else "FAIL"
        diag = f"diag {a.get('diagnoses_before','?')}->{a.get('diagnoses_after','?')}"
        meds = f"meds {a.get('medications_before','?')}->{a.get('medications_after','?')}"
        labs = f"labs {a.get('lab_results_before','?')}->{a.get('lab_results_after','?')}"
        print(f"  [{marker}] doc {a['document_id'][:8]}  {diag}  {meds}  {labs}"
              + (f"  ERROR: {a['error']}" if a["error"] else ""))
    print("-" * 70)
    print(f"Total: {result['total_documents']}  Succeeded: {result['succeeded']}  "
          f"Failed: {result['failed']}  Patients affected: {result['patients_affected']}  "
          f"Summaries rebuilt: {result['patient_summaries_rebuilt']}")
    return 0 if result["failed"] == 0 else 1


if __name__ == "__main__":
    sys.exit(asyncio.run(_main(_parse_args())))
