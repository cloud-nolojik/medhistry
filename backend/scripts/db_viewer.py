"""DB admin viewer — inspect and export patient records from a running container.

Designed to be run inside the API container so it gets DB creds from env:
    docker compose -f docker-compose.prod.yml exec api python -m scripts.db_viewer <cmd>

Commands:
    list                       — all patients (id, name, phone, #docs)
    show <name-fragment>       — full record + documents for matching patient(s)
    export <name-fragment>     — dump matching patient(s) to JSON + print path
    doctors                    — all doctors
    counts                     — row counts per table

Name matching is case-insensitive substring (ILIKE '%name%').
"""

from __future__ import annotations

import asyncio
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from uuid import UUID

from sqlalchemy import select, func, text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import async_session as AsyncSessionLocal
from app.models.patient import Patient
from app.models.medical_document import MedicalDocument
from app.models.doctor import Doctor
from app.models.patient_access_log import PatientAccessLog


def _ser(obj):
    """JSON-serialize UUIDs and datetimes."""
    if isinstance(obj, UUID):
        return str(obj)
    if isinstance(obj, datetime):
        return obj.isoformat()
    raise TypeError(f"not serializable: {type(obj)}")


def _row_to_dict(row) -> dict:
    d = {}
    for col in row.__table__.columns:
        val = getattr(row, col.name)
        if isinstance(val, UUID):
            val = str(val)
        if isinstance(val, datetime):
            val = val.isoformat()
        d[col.name] = val
    return d


async def cmd_list(session: AsyncSession) -> None:
    """List all patients with document counts."""
    q = (
        select(
            Patient.id,
            Patient.name,
            Patient.phone,
            Patient.date_of_birth,
            Patient.gender,
            Patient.relationship,
            func.count(MedicalDocument.id).label("docs"),
        )
        .outerjoin(MedicalDocument, MedicalDocument.patient_id == Patient.id)
        .group_by(Patient.id)
        .order_by(Patient.name)
    )
    result = await session.execute(q)
    rows = result.all()
    print(f"\n{'#':<3} {'Name':<30} {'Phone':<15} {'DOB':<12} {'Gender':<8} {'Docs':>5}")
    print("-" * 80)
    for i, r in enumerate(rows, 1):
        phone = r.phone or "—"
        dob = r.date_of_birth or "—"
        gender = (r.gender or "—")[:8]
        rel = f" ({r.relationship})" if r.relationship else ""
        name = (r.name + rel)[:30]
        print(f"{i:<3} {name:<30} {phone:<15} {dob:<12} {gender:<8} {r.docs:>5}")
    print(f"\nTotal patients: {len(rows)}\n")


async def _find_patients(session: AsyncSession, fragment: str) -> list[Patient]:
    q = select(Patient).where(Patient.name.ilike(f"%{fragment}%")).order_by(Patient.name)
    result = await session.execute(q)
    return list(result.scalars().all())


async def cmd_show(session: AsyncSession, fragment: str) -> None:
    patients = await _find_patients(session, fragment)
    if not patients:
        print(f"No patient matching '{fragment}'")
        return
    for p in patients:
        print("=" * 80)
        print(f"PATIENT: {p.name}  (id: {p.id})")
        print("=" * 80)
        for k, v in _row_to_dict(p).items():
            if k == "password_hash" or k == "pin_hash":
                v = "<redacted>" if v else None
            print(f"  {k}: {v}")

        docs_q = (
            select(MedicalDocument)
            .where(MedicalDocument.patient_id == p.id)
            .order_by(MedicalDocument.document_date.desc().nulls_last(),
                      MedicalDocument.created_at.desc())
        )
        docs = (await session.execute(docs_q)).scalars().all()
        print(f"\n  DOCUMENTS ({len(docs)}):")
        for d in docs:
            print(f"    • [{d.doc_type or '?':<20}] {d.document_date or '—'}  "
                  f"{d.filename}  ({d.file_size_bytes} bytes, {d.processing_status})")
            if d.ai_summary:
                preview = d.ai_summary[:120].replace("\n", " ")
                print(f"        summary: {preview}{'…' if len(d.ai_summary) > 120 else ''}")

        logs_q = (
            select(PatientAccessLog)
            .where(PatientAccessLog.patient_id == p.id)
            .order_by(PatientAccessLog.accessed_at.desc())
            .limit(10)
        )
        logs = (await session.execute(logs_q)).scalars().all()
        print(f"\n  RECENT ACCESS ({len(logs)} most recent):")
        for log in logs:
            print(f"    • {log.accessed_at.isoformat()}  method={log.method}  doctor={log.doctor_id}")
        print()


async def cmd_export(session: AsyncSession, fragment: str) -> None:
    patients = await _find_patients(session, fragment)
    if not patients:
        print(f"No patient matching '{fragment}'")
        return

    bundle = []
    for p in patients:
        patient_dict = _row_to_dict(p)
        patient_dict.pop("password_hash", None)
        patient_dict.pop("pin_hash", None)

        docs = (await session.execute(
            select(MedicalDocument).where(MedicalDocument.patient_id == p.id)
        )).scalars().all()
        logs = (await session.execute(
            select(PatientAccessLog).where(PatientAccessLog.patient_id == p.id)
        )).scalars().all()

        bundle.append({
            "patient": patient_dict,
            "documents": [_row_to_dict(d) for d in docs],
            "access_logs": [_row_to_dict(l) for l in logs],
        })

    safe_name = fragment.replace("/", "_").replace(" ", "_")
    ts = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
    out = Path(f"/tmp/patient_export_{safe_name}_{ts}.json")
    out.write_text(json.dumps(bundle, indent=2, default=_ser))
    print(f"Exported {len(bundle)} patient(s) to: {out}")
    print(f"Copy to host:  scp root@168.144.23.210:{out} ./")


async def cmd_doctors(session: AsyncSession) -> None:
    docs = (await session.execute(select(Doctor).order_by(Doctor.name))).scalars().all()
    print(f"\n{'Name':<30} {'Phone':<15} {'Specialty':<20} {'Verified':<8}")
    print("-" * 80)
    for d in docs:
        print(f"{(d.name or '—')[:30]:<30} {(d.phone or '—'):<15} "
              f"{(d.specialty or '—')[:20]:<20} {str(getattr(d, 'is_verified', '—'))[:8]:<8}")
    print(f"\nTotal doctors: {len(docs)}\n")


async def cmd_counts(session: AsyncSession) -> None:
    tables = [
        "patients", "doctors", "hospitals", "medical_documents",
        "patient_access_logs", "qr_sessions", "invitations", "otps",
    ]
    print("\nRow counts:")
    for t in tables:
        try:
            n = (await session.execute(text(f"SELECT COUNT(*) FROM {t}"))).scalar()
            print(f"  {t:<25} {n:>6}")
        except Exception as e:
            print(f"  {t:<25} ERROR: {e}")
    print()


async def main() -> None:
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    cmd = sys.argv[1]
    arg = sys.argv[2] if len(sys.argv) > 2 else None

    async with AsyncSessionLocal() as session:
        if cmd == "list":
            await cmd_list(session)
        elif cmd == "show":
            if not arg:
                print("Usage: show <name-fragment>"); sys.exit(1)
            await cmd_show(session, arg)
        elif cmd == "export":
            if not arg:
                print("Usage: export <name-fragment>"); sys.exit(1)
            await cmd_export(session, arg)
        elif cmd == "doctors":
            await cmd_doctors(session)
        elif cmd == "counts":
            await cmd_counts(session)
        else:
            print(f"Unknown command: {cmd}")
            print(__doc__)
            sys.exit(1)


if __name__ == "__main__":
    asyncio.run(main())
