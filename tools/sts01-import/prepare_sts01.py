"""STS01 -> Hanul AI-PACS demo preprocessing.

Scans a raw STS01 PACS dump, curates a representative subset of single-frame
grayscale instances, decompresses them to Explicit VR Little Endian, and
re-anonymizes them so the demo QC gate returns PASS.

The curated files are written into the seed sample directory
(tools/seed-dicoms/samples/user-provided) so the normal `make seed` flow
uploads them to Orthanc.

Why preprocessing is required
-----------------------------
The raw STS01 dump does NOT satisfy the demo's intentionally-narrow QC gate:
  * ~99.7% of instances use compressed transfer syntaxes (JPEG Lossless /
    Baseline / JPEG 2000) but the gate only accepts Explicit VR Little Endian.
  * PatientIDs are real-looking (MS0006, snuh_..., TEST-27-2) but the gate
    requires the anonymized ANON### pattern and blocks PHI.
  * Several source UIDs have leading-zero components, which the backend
    UidUtil rejects.
This script fixes all three so QC -> PASS and the full AI pipeline runs.

Usage (Docker, from the repo root)
----------------------------------
  docker run --rm \
    -v "$(pwd)/../../../STS01:/data:ro" \
    -v "$(pwd)/../seed-dicoms/samples/user-provided:/out" \
    -v "$(pwd):/work:ro" \
    python:3.12-slim sh -c \
    "pip install -r /work/requirements.txt && python /work/prepare_sts01.py"

Then upload to Orthanc:
  docker compose build seed-dicoms && docker compose run --rm seed-dicoms

Paths and per-modality counts can be overridden with env vars:
  STS01_SRC (default /data), STS01_OUT (default /out),
  STS01_TARGETS (e.g. "CR=6,CT=8,MR=4,DR=2,XA=1").
"""
from __future__ import annotations

import collections
import glob
import json
import os

import numpy as np
import pydicom
from pydicom.uid import ExplicitVRLittleEndian, generate_uid

SRC = os.getenv("STS01_SRC", "/data")
OUT = os.getenv("STS01_OUT", "/out")


def _targets():
    # How many representative series to pull per modality (one mid-slice each).
    default = {"CR": 4, "DR": 2, "DX": 2, "MG": 2, "CT": 4, "MR": 4, "US": 1, "XA": 1}
    raw = os.getenv("STS01_TARGETS")
    if not raw:
        return default
    out = {}
    for part in raw.split(","):
        if "=" in part:
            k, v = part.split("=", 1)
            out[k.strip().upper()] = int(v)
    return out or default


TARGET_PER_MODALITY = _targets()
GRAY = {"MONOCHROME1", "MONOCHROME2"}
ALLOWED_MOD = set(TARGET_PER_MODALITY)

PHI_TAGS = [
    "PatientBirthDate", "PatientBirthTime", "PatientAge", "PatientSize", "PatientWeight",
    "PatientAddress", "OtherPatientIDs", "OtherPatientIDsSequence", "OtherPatientNames",
    "InstitutionName", "InstitutionAddress", "InstitutionalDepartmentName",
    "ReferringPhysicianName", "PerformingPhysicianName", "NameOfPhysiciansReadingStudy",
    "OperatorsName", "RequestingPhysician", "RequestedProcedureDescription",
    "StationName", "DeviceSerialNumber", "AccessionNumber", "IssuerOfPatientID",
]


def s(ds, name, default=""):
    v = getattr(ds, name, default)
    return default if v is None else v


def scan():
    files = sorted(glob.glob(os.path.join(SRC, "**", "*.dcm"), recursive=True))
    cands = []
    skipped = collections.Counter()
    for f in files:
        try:
            ds = pydicom.dcmread(f, stop_before_pixels=True, force=True)
        except Exception:
            skipped["unreadable"] += 1
            continue
        mod = str(s(ds, "Modality")).upper()
        if mod not in ALLOWED_MOD:
            skipped[f"modality:{mod or 'NONE'}"] += 1
            continue
        spp = int(s(ds, "SamplesPerPixel", 1) or 1)
        nf = s(ds, "NumberOfFrames", None)
        photo = str(s(ds, "PhotometricInterpretation"))
        rows, cols = s(ds, "Rows", None), s(ds, "Columns", None)
        if spp != 1:
            skipped["color/multisample"] += 1
            continue
        if nf and int(nf) > 1:
            skipped["multiframe"] += 1
            continue
        if photo not in GRAY:
            skipped[f"photo:{photo or 'NONE'}"] += 1
            continue
        if not rows or not cols:
            skipped["no-dims"] += 1
            continue
        cands.append({
            "path": f, "mod": mod,
            "study": str(s(ds, "StudyInstanceUID")),
            "series": str(s(ds, "SeriesInstanceUID")),
            "pid": str(s(ds, "PatientID")),
            "inst": int(s(ds, "InstanceNumber", 0) or 0),
            "rows": int(rows), "cols": int(cols),
            "bodypart": str(s(ds, "BodyPartExamined")),
        })
    return files, cands, skipped


def select(cands):
    by_mod = collections.defaultdict(list)
    for c in cands:
        by_mod[c["mod"]].append(c)
    chosen = []
    for mod, cap in TARGET_PER_MODALITY.items():
        series_map = collections.defaultdict(list)
        for c in by_mod.get(mod, []):
            series_map[c["series"]].append(c)
        # prefer larger / well-formed series, deterministic order
        series_list = sorted(series_map.values(), key=lambda g: (-len(g), g[0]["series"]))
        for grp in series_list[:cap]:
            grp.sort(key=lambda c: (c["inst"], c["path"]))
            chosen.append(grp[len(grp) // 2])  # representative middle slice
    return chosen


def convert(chosen):
    os.makedirs(OUT, exist_ok=True)
    # clean any previous STS01 outputs so indices stay deterministic
    for old in glob.glob(os.path.join(OUT, "STS01_*.dcm")):
        os.remove(old)
    pid_map: dict[str, str] = {}
    # Backend UidUtil rejects components with leading zeros / >64 chars, which
    # several STS01 source UIDs violate. Remap to fresh valid UIDs, consistently
    # per original study/series so instances still group together in Orthanc.
    study_uid_map: dict[str, str] = {}
    series_uid_map: dict[str, str] = {}
    counter = [1]
    manifest = []

    def anon_for(pid):
        if pid not in pid_map:
            pid_map[pid] = f"ANON{counter[0]:03d}"
            counter[0] += 1
        return pid_map[pid]

    def remap(table, original):
        if original not in table:
            table[original] = generate_uid()
        return table[original]

    for i, c in enumerate(chosen, start=1):
        try:
            ds = pydicom.dcmread(c["path"], force=True)
            if "PixelData" not in ds:
                print(f"  skip (no PixelData): {c['path']}")
                continue
            arr = ds.pixel_array  # triggers decompression via installed handlers
        except Exception as e:
            print(f"  skip (decode failed: {type(e).__name__}: {e}): {c['path']}")
            continue

        # write uncompressed Explicit VR Little Endian
        ds.PixelData = arr.tobytes()
        ds["PixelData"].VR = "OW" if int(s(ds, "BitsAllocated", 16) or 16) > 8 else "OB"

        # remap UIDs to backend-valid ones (no leading-zero components, <=64 chars)
        sop_class = str(s(ds, "SOPClassUID"))
        if not sop_class:
            print(f"  skip (no SOPClassUID): {c['path']}")
            continue
        new_study = remap(study_uid_map, c["study"])
        new_series = remap(series_uid_map, c["series"])
        new_sop = generate_uid()
        ds.StudyInstanceUID = new_study
        ds.SeriesInstanceUID = new_series
        ds.SOPInstanceUID = new_sop

        if ds.file_meta is None:
            from pydicom.dataset import FileMetaDataset
            ds.file_meta = FileMetaDataset()
        ds.file_meta.TransferSyntaxUID = ExplicitVRLittleEndian
        ds.file_meta.MediaStorageSOPClassUID = sop_class
        ds.file_meta.MediaStorageSOPInstanceUID = new_sop

        # re-anonymize
        anon_id = anon_for(c["pid"])
        ds.PatientID = anon_id
        ds.PatientName = "ANON^DEMO"
        for tag in PHI_TAGS:
            if tag in ds:
                try:
                    del ds[tag]
                except Exception:
                    pass
        bp = (c["bodypart"] or "").upper().replace(" ", "_")
        ds.StudyDescription = f"STS01 {c['mod']} {bp} DEMO".replace("  ", " ").strip()
        ds.SeriesDescription = f"STS01 {c['mod']} SERIES DEMO"
        ds.BurnedInAnnotation = "NO"
        ds.remove_private_tags()

        fname = f"STS01_{c['mod']}_{i:03d}.dcm"
        out_path = os.path.join(OUT, fname)
        ds.save_as(out_path, enforce_file_format=True)
        manifest.append({
            "file": fname, "modality": c["mod"], "anonPatientId": anon_id,
            "rows": c["rows"], "cols": c["cols"],
            "studyInstanceUid": str(s(ds, "StudyInstanceUID")),
            "seriesInstanceUid": str(s(ds, "SeriesInstanceUID")),
            "sopInstanceUid": str(s(ds, "SOPInstanceUID")),
            "sourcePath": c["path"].replace(SRC + "/", "STS01/"),
        })
        print(f"  wrote {fname}  ({c['mod']} {c['rows']}x{c['cols']}  {anon_id})")
    return manifest


def validate(manifest):
    """Re-read outputs and assert QC-relevant invariants."""
    import re
    anon_re = re.compile(r"^ANON\d{3,}$")
    uid_re = re.compile(r"^[0-9]+(\.[0-9]+)*$")

    def valid_uid(u):
        u = str(u)
        if not u or len(u) > 64 or not uid_re.match(u):
            return False
        return all(not (len(p) > 1 and p.startswith("0")) for p in u.split("."))

    ok = 0
    for m in manifest:
        ds = pydicom.dcmread(os.path.join(OUT, m["file"]), force=True)
        ts = str(ds.file_meta.TransferSyntaxUID)
        rows, cols = int(ds.Rows), int(ds.Columns)
        ba = int(ds.BitsAllocated)
        plen = len(ds.PixelData)
        problems = []
        if ts != "1.2.840.10008.1.2.1":
            problems.append(f"TS={ts}")
        for uname in ("StudyInstanceUID", "SeriesInstanceUID", "SOPInstanceUID", "SOPClassUID"):
            if not valid_uid(s(ds, uname)):
                problems.append(f"{uname}={s(ds, uname)}")
        if not anon_re.match(str(ds.PatientID)):
            problems.append(f"PID={ds.PatientID}")
        if plen < rows * cols * ba // 8:
            problems.append(f"pixlen={plen}<{rows*cols*ba//8}")
        if str(s(ds, "PhotometricInterpretation")) not in GRAY:
            problems.append(f"photo={s(ds,'PhotometricInterpretation')}")
        if int(s(ds, "SamplesPerPixel", 1) or 1) != 1:
            problems.append("spp!=1")
        if any(t.is_private for t in ds):
            problems.append("private-tags")
        if problems:
            print(f"  !! {m['file']}: {', '.join(problems)}")
        else:
            ok += 1
    print(f"validation: {ok}/{len(manifest)} clean")
    return ok


def main():
    print("== scanning STS01 ==")
    files, cands, skipped = scan()
    print(f"total dcm: {len(files)}  candidates (single-frame grayscale, allowed modality): {len(cands)}")
    print("skip reasons (top):")
    for k, v in skipped.most_common(12):
        print(f"  {v:5d}  {k}")
    by_mod = collections.Counter(c["mod"] for c in cands)
    print("candidate modalities:", dict(by_mod))

    print("\n== selecting representative subset ==")
    chosen = select(cands)
    print(f"selected {len(chosen)} instances:", dict(collections.Counter(c['mod'] for c in chosen)))

    print("\n== converting + anonymizing ==")
    manifest = convert(chosen)

    print("\n== validating outputs ==")
    validate(manifest)

    with open(os.path.join(OUT, "STS01_curated_manifest.json"), "w", encoding="utf-8") as fh:
        json.dump({"count": len(manifest), "items": manifest}, fh, indent=2, ensure_ascii=False)
    print(f"\nwrote {len(manifest)} curated DICOM files + STS01_curated_manifest.json to {OUT}")


if __name__ == "__main__":
    main()
