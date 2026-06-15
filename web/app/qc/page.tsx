"use client";

import { ChangeEvent, useEffect, useState } from "react";
import { ClipboardCheck, Upload } from "lucide-react";
import { apiFetch, InstanceSummary, QcReport, StudySummary } from "@/lib/api";
import { Card, PageTitle, StateMessage, StatusBadge } from "@/components/ui";

type DemoManifest = {
  datasetVersion: string;
  cases: { caseId: string; caseType: string; patientId: string; expectedQcStatus: string; upload: boolean; notes: string }[];
};

export default function QcPage() {
  const [studies, setStudies] = useState<StudySummary[]>([]);
  const [instances, setInstances] = useState<InstanceSummary[]>([]);
  const [studyUid, setStudyUid] = useState("");
  const [instanceKey, setInstanceKey] = useState("");
  const [report, setReport] = useState<QcReport | null>(null);
  const [manifest, setManifest] = useState<DemoManifest | null>(null);
  const [message, setMessage] = useState("");

  // QC 페이지는 Orthanc에 seed된 study를 기준으로 시작하므로, 목록을 먼저 가져와 기본 study를 잡는다.
  useEffect(() => {
    apiFetch<StudySummary[]>("/api/studies").then((result) => {
      setStudies(result);
      setStudyUid(result[0]?.studyInstanceUid || "");
    }).catch((err) => setMessage(err.message));
  }, []);

  // manifest는 데모 데이터셋의 의도된 PASS/WARN/FAIL 기대값을 UI에서 확인하기 위한 참고 정보다.
  useEffect(() => {
    apiFetch<DemoManifest>("/api/demo/manifest").then(setManifest).catch(() => setManifest(null));
  }, []);

  // study 선택이 바뀌면 해당 study의 instance 목록을 다시 가져와 첫 항목을 검증 대상으로 잡는다.
  useEffect(() => {
    if (!studyUid) return;
    apiFetch<InstanceSummary[]>(`/api/studies/${studyUid}/instances`).then((result) => {
      setInstances(result);
      const first = result[0];
      setInstanceKey(first ? `${first.studyInstanceUid}|${first.seriesInstanceUid}|${first.sopInstanceUid}` : "");
    }).catch((err) => setMessage(err.message));
  }, [studyUid]);

  async function validateSelected() {
    const [studyInstanceUid, seriesInstanceUid, sopInstanceUid] = instanceKey.split("|");
    if (!sopInstanceUid) return;
    setMessage("");
    try {
      // 저장된 DICOM은 UID만 전달하고, 백엔드가 Orthanc에서 원본을 다시 읽어 QC를 수행한다.
      const result = await apiFetch<QcReport>("/api/qc/validate", {
        method: "POST",
        body: JSON.stringify({ studyInstanceUid, seriesInstanceUid, sopInstanceUid })
      });
      setReport(result);
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "QC에 실패했습니다");
    }
  }

  async function validateStudyByPatientId(patientId: string) {
    // seed manifest의 대표 케이스를 버튼 한 번으로 재현하기 위해 PatientID로 study를 찾는다.
    const study = studies.find((item) => item.patientId === patientId);
    if (!study) {
      setMessage(`${patientId} seeded study를 찾지 못했습니다. make seed를 실행해 주세요.`);
      return;
    }
    setStudyUid(study.studyInstanceUid);
    setMessage("");
    try {
      const instanceResult = await apiFetch<InstanceSummary[]>(`/api/studies/${study.studyInstanceUid}/instances`);
      const first = instanceResult[0];
      if (!first) throw new Error("선택한 study에 인스턴스가 없습니다");
      setInstances(instanceResult);
      setInstanceKey(`${first.studyInstanceUid}|${first.seriesInstanceUid}|${first.sopInstanceUid}`);
      const result = await apiFetch<QcReport>("/api/qc/validate", {
        method: "POST",
        body: JSON.stringify({
          studyInstanceUid: first.studyInstanceUid,
          seriesInstanceUid: first.seriesInstanceUid,
          sopInstanceUid: first.sopInstanceUid
        })
      });
      setReport(result);
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "데모 QC 실행에 실패했습니다");
    }
  }

  async function uploadInvalid(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    const form = new FormData();
    form.append("file", file);
    setMessage("");
    try {
      // 업로드 검증은 PACS 저장 없이 파일 자체의 DICOM 구조와 PHI 위험만 확인한다.
      const result = await apiFetch<QcReport>("/api/qc/validate-upload", { method: "POST", body: form });
      setReport(result);
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "업로드 QC에 실패했습니다");
    }
  }

  async function validateBytes(name: string, bytes: Uint8Array) {
    const form = new FormData();
    const body = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
    form.append("file", new Blob([body], { type: "application/dicom" }), name);
    setMessage("");
    try {
      // negative case는 브라우저에서 즉석으로 만든 bytes를 validate-upload로 보내 차단 경로를 보여준다.
      const result = await apiFetch<QcReport>("/api/qc/validate-upload", { method: "POST", body: form });
      setReport(result);
    } catch (err) {
      setMessage(err instanceof Error ? err.message : "업로드 QC에 실패했습니다");
    }
  }

  return (
    <>
      <PageTitle title="QC 게이트" subtitle="AI 추론 전에 DICOM 품질을 검증합니다. 잘못되었거나 안전하지 않은 입력은 추론 경로에서 차단됩니다." />
      {message && <StateMessage type="error" title="QC 메시지" detail={message} />}
      <div className="mt-4 grid gap-4 lg:grid-cols-[0.85fr_1.15fr]">
        <div className="space-y-4">
          <Card>
            <div className="mb-3 flex items-center gap-2 text-sm font-medium"><ClipboardCheck size={18} className="text-cyanSoft" /> Orthanc 인스턴스 선택</div>
            <label className="block text-sm text-slate-400">
              검사
              <select value={studyUid} onChange={(event) => setStudyUid(event.target.value)} className="mt-2 w-full rounded-md border border-line bg-[#0b1322] px-3 py-2 text-slate-100 outline-none">
                {studies.map((study) => <option key={study.studyInstanceUid} value={study.studyInstanceUid}>{study.patientId} / {study.studyDescription || "설명 없음"}</option>)}
              </select>
            </label>
            <label className="mt-3 block text-sm text-slate-400">
              인스턴스
              <select value={instanceKey} onChange={(event) => setInstanceKey(event.target.value)} className="mt-2 w-full rounded-md border border-line bg-[#0b1322] px-3 py-2 text-slate-100 outline-none">
                {instances.map((instance) => (
                  <option key={instance.sopInstanceUid} value={`${instance.studyInstanceUid}|${instance.seriesInstanceUid}|${instance.sopInstanceUid}`}>
                    {instance.instanceNumber || "1"} / {instance.sopInstanceUid}
                  </option>
                ))}
              </select>
            </label>
            <button onClick={validateSelected} className="mt-4 w-full rounded-md bg-cyanSoft px-3 py-2 font-semibold text-slate-950 hover:bg-teal-200">QC 실행</button>
          </Card>

          <Card>
            <div className="mb-3 text-sm font-medium">데모 QC 케이스</div>
            <div className="grid gap-2">
              <button onClick={() => validateStudyByPatientId("ANON101")} className="rounded-md border border-line px-3 py-2 text-left text-sm hover:bg-slate-800">Run breast CR sample</button>
              <button onClick={() => validateStudyByPatientId("ANON102")} className="rounded-md border border-line px-3 py-2 text-left text-sm hover:bg-slate-800">Run brain CT sample</button>
              <button onClick={() => validateBytes("NEG001_phi_like_negative_case.dcm", demoDicomBytes({ patientId: "REAL123", patientName: "JOHN^SMITH", includePixelData: true }))} className="rounded-md border border-line px-3 py-2 text-left text-sm hover:bg-slate-800">Run FAIL PHI-like case</button>
              <button onClick={() => validateBytes("NEG003_corrupted_invalid_file.dcm", new TextEncoder().encode("This is intentionally not a DICOM file."))} className="rounded-md border border-line px-3 py-2 text-left text-sm hover:bg-slate-800">Run FAIL corrupted file case</button>
              <button onClick={() => validateBytes("NEG002_missing_pixeldata_negative_case.dcm", demoDicomBytes({ patientId: "ANON901", patientName: "ANON^DEMO901", includePixelData: false }))} className="rounded-md border border-line px-3 py-2 text-left text-sm hover:bg-slate-800">Run FAIL missing PixelData case</button>
            </div>
          </Card>

          <Card>
            <div className="mb-3 flex items-center gap-2 text-sm font-medium"><Upload size={18} className="text-amberSoft" /> 직접 DICOM 업로드</div>
            <p className="mb-3 text-sm text-slate-400">Negative case는 기본적으로 Orthanc에 올리지 않습니다. `tools/seed-dicoms/output/manifest.json`에서 생성 목록을 확인할 수 있습니다.</p>
            <input type="file" accept=".dcm,application/dicom,*/*" onChange={uploadInvalid} className="w-full rounded-md border border-line bg-[#0b1322] px-3 py-2 text-sm" />
          </Card>

          {manifest && (
            <Card>
              <div className="mb-3 text-sm font-medium">Bundled DICOM manifest</div>
              <div className="text-xs text-slate-500">Version {manifest.datasetVersion}</div>
              <div className="mt-3 max-h-56 space-y-2 overflow-auto text-xs">
                {manifest.cases.map((item) => (
                  <div key={item.caseId} className="rounded-md border border-line bg-[#0b1322] p-2">
                    <div className="flex items-center justify-between gap-2">
                      <span>{item.caseType}</span>
                      <StatusBadge value={item.expectedQcStatus} />
                    </div>
                    <div className="mt-1 text-slate-500">{item.caseId} / upload={String(item.upload)}</div>
                  </div>
                ))}
              </div>
            </Card>
          )}
        </div>

        <Card>
          <div className="mb-4 flex items-center justify-between gap-3">
            <div className="text-sm font-medium">QC 보고서</div>
            <StatusBadge value={report?.status || "NOT_RUN"} />
          </div>
          {report && (
            <div className={`mb-3 rounded-md border px-3 py-2 text-sm ${report.status === "FAIL" ? "border-roseSoft/40 bg-rose-950/20 text-rose-100" : "border-cyanSoft/40 bg-teal-950/20 text-cyan-100"}`}>
              AI 추론 {report.status === "FAIL" ? "차단" : "허용"}: {report.status === "FAIL" ? "필수 안전성 또는 유효성 검사를 통과하지 못했습니다." : "blocking ERROR가 없어 데모 AI 분석을 실행할 수 있습니다."}
            </div>
          )}
          {!report ? (
            <StateMessage type="empty" title="아직 보고서가 없습니다" detail="인스턴스를 선택하거나 손상된 데모 파일을 업로드해 주세요." />
          ) : (
            <div className="space-y-2">
              {report.checks.map((check) => (
                <div key={check.name} className="rounded-md border border-line bg-[#0b1322] p-3 text-sm">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <div className="font-medium">{check.name}</div>
                      {check.category && <div className="mt-1 text-xs text-slate-500">{check.category}</div>}
                    </div>
                    <StatusBadge value={check.passed ? check.severity : `FAIL ${check.severity}`} />
                  </div>
                  <div className="mt-1 text-slate-400">{check.message}</div>
                  {(check.observed || check.expectedHint) && (
                    <div className="mt-2 grid gap-2 text-xs md:grid-cols-2">
                      <div className="rounded-md border border-line bg-panel p-2"><span className="text-slate-500">Observed</span><div className="mt-1 break-all">{check.observed}</div></div>
                      <div className="rounded-md border border-line bg-panel p-2"><span className="text-slate-500">Expected</span><div className="mt-1 break-all">{check.expectedHint}</div></div>
                    </div>
                  )}
                  {!check.passed && check.suggestedFix && <div className="mt-2 text-xs text-amber-100">{check.suggestedFix}</div>}
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>
    </>
  );
}

// 브라우저에서 최소 DICOM Part 10 형태를 직접 만들어 PHI-like, PixelData 누락 같은 실패 케이스를 재현한다.
function demoDicomBytes({ patientId, patientName, includePixelData }: { patientId: string; patientName: string; includePixelData: boolean }) {
  const parts: number[] = [...new Array(128).fill(0), ...ascii("DICM")];
  element(parts, 0x0002, 0x0010, "UI", asciiValue("1.2.840.10008.1.2.1"));
  element(parts, 0x0008, 0x0016, "UI", asciiValue("1.2.840.10008.5.1.4.1.1.7"));
  element(parts, 0x0008, 0x0018, "UI", asciiValue("1.2.826.0.1.3680043.10.543.9.1.1"));
  element(parts, 0x0008, 0x0060, "CS", asciiValue("DX"));
  element(parts, 0x0008, 0x1030, "LO", asciiValue("CHEST DEMO NEGATIVE"));
  element(parts, 0x0008, 0x103e, "LO", asciiValue("PA CHEST SYNTHETIC"));
  element(parts, 0x0010, 0x0010, "PN", asciiValue(patientName));
  element(parts, 0x0010, 0x0020, "LO", asciiValue(patientId));
  element(parts, 0x0020, 0x000d, "UI", asciiValue("1.2.826.0.1.3680043.10.543.9"));
  element(parts, 0x0020, 0x000e, "UI", asciiValue("1.2.826.0.1.3680043.10.543.9.1"));
  element(parts, 0x0028, 0x0002, "US", ushort(1));
  element(parts, 0x0028, 0x0004, "CS", asciiValue("MONOCHROME2"));
  element(parts, 0x0028, 0x0010, "US", ushort(1));
  element(parts, 0x0028, 0x0011, "US", ushort(2));
  element(parts, 0x0028, 0x0100, "US", ushort(16));
  element(parts, 0x0028, 0x0101, "US", ushort(12));
  element(parts, 0x0028, 0x0301, "CS", asciiValue("NO"));
  if (includePixelData) {
    element(parts, 0x7fe0, 0x0010, "OW", [1, 0, 2, 0]);
  }
  return new Uint8Array(parts);
}

// Explicit VR Little Endian 요소만 생성한다. 데모 QC가 읽는 핵심 태그만 넣기 위한 작은 writer다.
function element(parts: number[], group: number, elem: number, vr: string, value: number[]) {
  const padded = value.length % 2 === 0 ? value : [...value, 0];
  parts.push(group & 0xff, (group >> 8) & 0xff, elem & 0xff, (elem >> 8) & 0xff, ...ascii(vr));
  if (["OB", "OW", "SQ", "UN", "UT"].includes(vr)) {
    parts.push(0, 0, padded.length & 0xff, (padded.length >> 8) & 0xff, (padded.length >> 16) & 0xff, (padded.length >> 24) & 0xff);
  } else {
    parts.push(padded.length & 0xff, (padded.length >> 8) & 0xff);
  }
  parts.push(...padded);
}

function asciiValue(value: string) {
  return ascii(value);
}

function ascii(value: string) {
  return Array.from(value).map((ch) => ch.charCodeAt(0));
}

function ushort(value: number) {
  return [value & 0xff, (value >> 8) & 0xff];
}
