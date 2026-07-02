"use client";

import { useParams, useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { BrainCircuit, ClipboardCheck, ImageIcon, RefreshCw, RotateCcw, ZoomIn, ZoomOut } from "lucide-react";
import { apiFetch, assetUrl, InstanceSummary, QcReport, StudySummary } from "@/lib/api";
import { DicomCanvasViewer } from "@/components/dicom/dicom-canvas-viewer";
import { Card, CopyButton, Disclaimer, PageTitle, StateMessage, StatusBadge, displayStatus } from "@/components/ui";

type Metadata = { tags: Record<string, any> };

export default function StudyDetailPage() {
  const params = useParams<{ studyInstanceUid: string }>();
  const router = useRouter();
  const studyUid = decodeURIComponent(params.studyInstanceUid);
  const [study, setStudy] = useState<StudySummary | null>(null);
  const [instances, setInstances] = useState<InstanceSummary[]>([]);
  const [selected, setSelected] = useState<InstanceSummary | null>(null);
  const [metadata, setMetadata] = useState<Metadata | null>(null);
  const [windowPreset, setWindowPreset] = useState("chest");
  const [manualWindow, setManualWindow] = useState(false);
  const [windowCenter, setWindowCenter] = useState(1500);
  const [windowWidth, setWindowWidth] = useState(2200);
  const [zoom, setZoom] = useState(1);
  const [qcReport, setQcReport] = useState<QcReport | null>(null);
  const [toast, setToast] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  // Study 상세 진입 시 study 요약과 instance 목록을 같이 가져와 첫 instance를 기본 선택한다.
  useEffect(() => {
    Promise.all([
      apiFetch<StudySummary>(`/api/studies/${studyUid}`),
      apiFetch<InstanceSummary[]>(`/api/studies/${studyUid}/instances`)
    ])
      .then(([studyResult, instanceResult]) => {
        setStudy(studyResult);
        setInstances(instanceResult);
        setSelected(instanceResult[0] || null);
      })
      .catch((err) => setError(err.message));
  }, [studyUid]);

  // instance를 바꾸면 DICOM tag metadata도 함께 갱신해 viewer와 우측 패널이 같은 대상을 가리키게 한다.
  useEffect(() => {
    if (!selected) return;
    apiFetch<Metadata>(`/api/instances/${selected.studyInstanceUid}/${selected.seriesInstanceUid}/${selected.sopInstanceUid}/metadata`)
      .then(setMetadata)
      .catch((err) => setToast(err.message));
  }, [selected]);

  // Canvas viewer는 Orthanc 직접 접근 대신 gateway API의 원본 DICOM stream을 사용한다.
  const dicomPath = useMemo(() => {
    if (!selected) return "";
    return `/api/instances/${selected.studyInstanceUid}/${selected.seriesInstanceUid}/${selected.sopInstanceUid}/dicom`;
  }, [selected]);

  const dicomDownloadUrl = selected ? assetUrl(dicomPath) : "";
  const metadataRows = [
    "PatientID",
    "PatientName",
    "StudyDate",
    "StudyDescription",
    "SeriesDescription",
    "Modality",
    "SOPClassUID",
    "TransferSyntaxUID",
    "Rows",
    "Columns",
    "BitsAllocated",
    "BitsStored",
    "PhotometricInterpretation"
  ];

  function selectPreset(value: string) {
    // preset 선택은 수동 WL/WW를 해제해 DICOM tag나 지정 preset 값을 다시 사용하게 한다.
    setWindowPreset(value);
    setManualWindow(false);
  }

  async function runQc() {
    if (!selected) return;
    setBusy(true);
    setToast("");
    try {
      // QC는 선택된 instance의 세 UID를 기준으로 PACS에서 원본 DICOM을 다시 읽어 검증한다.
      const report = await apiFetch<QcReport>("/api/qc/validate", {
        method: "POST",
        body: JSON.stringify({
          studyInstanceUid: selected.studyInstanceUid,
          seriesInstanceUid: selected.seriesInstanceUid,
          sopInstanceUid: selected.sopInstanceUid
        })
      });
      setQcReport(report);
      setToast(`QC ${displayStatus(report.status)}`);
    } catch (err) {
      setToast(err instanceof Error ? err.message : "QC에 실패했습니다");
    } finally {
      setBusy(false);
    }
  }

  async function runAi() {
    if (!selected) return;
    setBusy(true);
    setToast("");
    try {
      // AI 작업은 서버에서 비동기로 진행되므로 생성 직후 job 상세 화면으로 이동해 상태를 추적한다.
      const result = await apiFetch<{ jobId: string }>("/api/ai/jobs", {
        method: "POST",
        body: JSON.stringify({
          studyInstanceUid: selected.studyInstanceUid,
          seriesInstanceUid: selected.seriesInstanceUid,
          sopInstanceUid: selected.sopInstanceUid,
          windowPreset
        })
      });
      router.push(`/ai/jobs/${result.jobId}`);
    } catch (err) {
      setToast(err instanceof Error ? err.message : "AI 작업에 실패했습니다");
      setBusy(false);
    }
  }

  if (error) return <StateMessage type="error" title="검사 상세를 불러오지 못했습니다" detail={error} />;
  if (!study || !selected) return <StateMessage type="loading" title="검사 상세를 불러오는 중" detail="인스턴스와 메타데이터를 조회하고 있습니다." />;

  return (
    <>
      <PageTitle title={study.patientId || "합성 검사"} subtitle={study.studyDescription || "선택 항목인 StudyDescription이 비어 있는 합성 검사입니다."} />
      {toast && <div className="mb-4 rounded-md border border-blueSoft/40 bg-blue-950/20 px-3 py-2 text-sm text-blue-100">{toast}</div>}
      <div className="grid gap-4 xl:grid-cols-[1.25fr_0.75fr]">
        <Card>
          <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2 text-sm font-medium"><ImageIcon size={18} className="text-cyanSoft" /> 인스턴스 미리보기</div>
            <div className="flex flex-wrap gap-2">
              {/* 데모 영상 특성에 맞춘 WL/WW preset이다. Auto는 pixel 분포를 샘플링해 화면용 범위를 계산한다. */}
              {[
                ["chest", "Chest"],
                ["lung", "Lung"],
                ["bone", "Bone"],
                ["auto", "Auto"]
              ].map(([value, label]) => (
                <button key={value} onClick={() => selectPreset(value)} className={`rounded-md border px-3 py-2 text-sm ${!manualWindow && windowPreset === value ? "border-cyanSoft bg-teal-950/40 text-cyanSoft" : "border-line hover:bg-slate-800"}`}>
                  {label}
                </button>
              ))}
            </div>
          </div>
          <DicomCanvasViewer
            dicomPath={dicomPath}
            windowPreset={windowPreset}
            windowCenter={manualWindow ? windowCenter : undefined}
            windowWidth={manualWindow ? windowWidth : undefined}
            zoom={zoom}
            className="max-h-[640px]"
          />
          <div className="mt-3 grid gap-3 lg:grid-cols-[1fr_180px]">
            <div className="rounded-md border border-line bg-[#0b1322] p-3">
              <div className="mb-2 text-xs text-slate-500">수동 WL/WW</div>
              {/* 수동 조절을 시작하면 preset보다 입력한 Window Center/Width를 우선 적용한다. */}
              <label className="grid gap-2 text-xs text-slate-300 sm:grid-cols-[120px_1fr_72px]">
                <span>Window Center</span>
                <input type="range" min="-1000" max="3000" step="10" value={windowCenter} onChange={(event) => { setManualWindow(true); setWindowCenter(Number(event.target.value)); }} />
                <input type="number" value={windowCenter} onChange={(event) => { setManualWindow(true); setWindowCenter(Number(event.target.value)); }} className="rounded-md border border-line bg-panel px-2 py-1" />
              </label>
              <label className="mt-2 grid gap-2 text-xs text-slate-300 sm:grid-cols-[120px_1fr_72px]">
                <span>Window Width</span>
                <input type="range" min="1" max="5000" step="10" value={windowWidth} onChange={(event) => { setManualWindow(true); setWindowWidth(Number(event.target.value)); }} />
                <input type="number" min="1" value={windowWidth} onChange={(event) => { setManualWindow(true); setWindowWidth(Number(event.target.value)); }} className="rounded-md border border-line bg-panel px-2 py-1" />
              </label>
            </div>
            <div className="rounded-md border border-line bg-[#0b1322] p-3">
              <div className="mb-2 text-xs text-slate-500">이미지 도구</div>
              <div className="flex gap-2">
                <button onClick={() => setZoom((value) => Math.min(3, Number((value + 0.25).toFixed(2))))} className="rounded-md border border-line p-2 hover:bg-slate-800" title="확대"><ZoomIn size={16} /></button>
                <button onClick={() => setZoom((value) => Math.max(0.5, Number((value - 0.25).toFixed(2))))} className="rounded-md border border-line p-2 hover:bg-slate-800" title="축소"><ZoomOut size={16} /></button>
                <button onClick={() => setZoom(1)} className="rounded-md border border-line p-2 hover:bg-slate-800" title="줌 초기화"><RotateCcw size={16} /></button>
              </div>
              <div className="mt-2 text-xs text-slate-500">Zoom {zoom.toFixed(2)}x</div>
            </div>
          </div>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <a href={dicomDownloadUrl} className="inline-flex rounded-md border border-line px-3 py-2 text-sm text-slate-300 hover:bg-slate-800">
              원본 DICOM 열기
            </a>
            <span className="text-xs text-slate-500">데모 preview renderer입니다. 진단용 viewer가 아닙니다.</span>
          </div>
        </Card>

        <div className="space-y-4">
          <Disclaimer />
          <Card>
            <div className="mb-3 text-sm font-medium">인스턴스</div>
            <div className="max-h-56 space-y-2 overflow-auto">
              {instances.map((instance) => (
                <button
                  key={instance.sopInstanceUid}
                  onClick={() => setSelected(instance)}
                  className={`w-full rounded-md border p-3 text-left text-xs ${selected.sopInstanceUid === instance.sopInstanceUid ? "border-cyanSoft bg-teal-950/30" : "border-line bg-[#0b1322] hover:bg-slate-800"}`}
                >
                  <div className="font-mono text-slate-300">{instance.sopInstanceUid}</div>
                  <div className="mt-1 text-slate-500">인스턴스 {instance.instanceNumber || "1"}</div>
                </button>
              ))}
            </div>
          </Card>

          <Card>
            <div className="mb-3 flex items-center gap-2 text-sm font-medium"><ClipboardCheck size={18} className="text-amberSoft" /> QC 패널</div>
            <div className="mb-3 flex items-center gap-2"><StatusBadge value={qcReport?.status || study.qcStatus} /></div>
            <button disabled={busy} onClick={runQc} className="inline-flex items-center gap-2 rounded-md border border-line px-3 py-2 text-sm hover:bg-slate-800 disabled:opacity-60">
              <RefreshCw size={16} /> QC 실행
            </button>
            {qcReport && (
              <div className="mt-3 max-h-48 space-y-2 overflow-auto text-xs">
                {qcReport.checks.map((check) => (
                  <div key={check.name} className="rounded-md border border-line bg-[#0b1322] p-2">
                    <div className="flex items-center justify-between gap-2">
                      <span>{check.name}</span>
                      <StatusBadge value={check.passed ? check.severity : `FAIL ${check.severity}`} />
                    </div>
                    <div className="mt-1 text-slate-500">{check.message}</div>
                  </div>
                ))}
              </div>
            )}
          </Card>

          <Card>
            <div className="mb-3 flex items-center gap-2 text-sm font-medium"><BrainCircuit size={18} className="text-cyanSoft" /> AI 실행</div>
            <button disabled={busy} onClick={runAi} className="w-full rounded-md bg-cyanSoft px-3 py-2 font-semibold text-slate-950 hover:bg-teal-200 disabled:opacity-60">
              AI 분석 실행
            </button>
          </Card>
        </div>
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card>
          <div className="mb-3 text-sm font-medium">핵심 메타데이터</div>
          <div className="space-y-2 text-xs">
            {metadataRows.map((key) => [key, metadata?.tags?.[key]] as const).map(([key, value]) => (
              <div key={key} className="flex items-center justify-between gap-3 rounded-md border border-line bg-[#0b1322] p-2">
                <span className="text-slate-500">{key}</span>
                <span className="flex min-w-0 items-center gap-2 font-mono text-slate-300">
                  <span className="truncate">{String(value ?? "")}</span>
                  {String(value ?? "").includes(".") && <CopyButton value={String(value)} />}
                </span>
              </div>
            ))}
          </div>
        </Card>
        <Card>
          <div className="mb-3 text-sm font-medium">검사 UID</div>
          {[study.studyInstanceUid, selected.seriesInstanceUid, selected.sopInstanceUid].map((uid) => (
            <div key={uid} className="mb-2 flex items-center justify-between gap-2 rounded-md border border-line bg-[#0b1322] p-2 text-xs">
              <span className="truncate font-mono text-slate-300">{uid}</span>
              <CopyButton value={uid} />
            </div>
          ))}
        </Card>
      </div>
    </>
  );
}
