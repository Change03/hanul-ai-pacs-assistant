"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { BrainCircuit, Download, Flame, Image as ImageIcon } from "lucide-react";
import { AiJob, ResultDicomMetadata, apiFetch, assetUrl } from "@/lib/api";
import { DicomCanvasViewer } from "@/components/dicom-canvas-viewer";
import { Card, CopyButton, Disclaimer, PageTitle, StateMessage, StatusBadge, displayFindingLabel, displayStatus } from "@/components/ui";

const preprocessingLabels: Record<string, string> = {
  rows: "행",
  columns: "열",
  windowCenter: "윈도우 중심",
  windowWidth: "윈도우 폭",
  rescaleSlope: "Rescale Slope",
  rescaleIntercept: "Rescale Intercept"
};

export default function AiJobPage() {
  const params = useParams<{ jobId: string }>();
  const jobId = params.jobId;
  const [job, setJob] = useState<AiJob | null>(null);
  const [metadata, setMetadata] = useState<ResultDicomMetadata | null>(null);
  const [error, setError] = useState("");
  const [overlayOpacity, setOverlayOpacity] = useState(70);
  const [showHeatmap, setShowHeatmap] = useState(true);
  const [showBoxes, setShowBoxes] = useState(true);

  // AI 작업은 백엔드 executor에서 비동기로 진행되므로, 상세 화면은 주기적으로 최신 상태를 polling한다.
  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const result = await apiFetch<AiJob>(`/api/ai/jobs/${jobId}`);
        if (active) {
          setJob(result);
          setError("");
        }
      } catch (err) {
        if (active) setError(err instanceof Error ? err.message : "작업을 불러오지 못했습니다");
      }
    }
    load();
    const timer = window.setInterval(load, 2000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [jobId]);

  // 결과 DICOM metadata는 파일 생성과 STOW 이후에만 의미가 있으므로 완료 계열 상태에서 별도로 조회한다.
  useEffect(() => {
    if (!job || !(job.status === "COMPLETED_VERIFIED" || job.status === "COMPLETED_UNVERIFIED" || job.status === "COMPLETED")) return;
    apiFetch<ResultDicomMetadata>(`/api/ai/jobs/${jobId}/result-metadata`)
      .then(setMetadata)
      .catch(() => setMetadata(null));
  }, [job, jobId]);

  if (error) return <StateMessage type="error" title="AI 작업을 불러오지 못했습니다" detail={error} />;
  if (!job) return <StateMessage type="loading" title="AI 작업을 불러오는 중" detail="게이트웨이 작업 상태를 기다리고 있습니다." />;

  // COMPLETED_* 상태는 결과 생성 이후 PACS 저장과 read-back 검증까지 UI에 드러내기 위한 상태다.
  const done = job.status === "COMPLETED" || job.status === "COMPLETED_VERIFIED" || job.status === "COMPLETED_UNVERIFIED";
  // 원본/결과 DICOM 모두 브라우저가 직접 Orthanc에 붙지 않고 백엔드 gateway를 통해 가져온다.
  const originalDicomPath = `/api/instances/${job.studyInstanceUid}/${job.seriesInstanceUid}/${job.sopInstanceUid}/dicom`;
  const resultDicomPath = `/api/ai/jobs/${job.id}/result-dicom`;
  // AI 서비스 응답 JSON은 결과 카드, QC 요약, 전처리 정보, 경계 상자 표시에서 공통으로 사용한다.
  const qc = job.result?.qc as any;
  const preprocessing = (job.result?.preprocessing || {}) as Record<string, any>;
  const boxes = (job.result?.boxes || []) as any[];

  return (
    <>
      <PageTitle title="AI 작업 결과" subtitle="선택한 합성 인스턴스에서 생성한 오버레이와 Secondary Capture DICOM 결과입니다." />
      <div className="mb-4 grid gap-3 lg:grid-cols-[1fr_260px]">
        <div className="space-y-2">
          <Disclaimer />
          {job.modelProvider === "DEMO_FALLBACK" && (
            <div className="rounded-md border border-blueSoft/40 bg-blue-950/20 px-3 py-2 text-sm text-blue-100">
              This is a deterministic demo algorithm, not a clinically validated AI model.
            </div>
          )}
        </div>
        <Card className="flex items-center justify-between gap-3">
          <div>
            <div className="text-xs text-slate-500">작업</div>
            <div className="font-mono text-xs text-slate-300">{job.id}</div>
          </div>
          <StatusBadge value={job.status} />
        </Card>
      </div>

      {!done && (
        <Card className="mb-4">
          <div className="flex items-center gap-3 text-sm text-slate-300">
            <BrainCircuit className="text-cyanSoft" size={20} />
            <div>
              <div className="font-medium text-slate-100">{displayStatus(job.status)}</div>
              <div className="text-slate-500">{job.errorMessage || "QC, 추론, 산출물 저장, STOW-RS 전송은 게이트웨이가 처리합니다."}</div>
            </div>
          </div>
        </Card>
      )}

      <div className="grid gap-4 xl:grid-cols-[1.25fr_0.75fr]">
        <Card>
          {/* 원본 DICOM, PNG overlay, 생성된 Secondary Capture DICOM을 나란히 놓아 결과를 검토한다. */}
          <div className="mb-3 flex items-center gap-2 text-sm font-medium"><ImageIcon size={18} className="text-blueSoft" /> 원본 / 오버레이 / 결과 비교</div>
          <div className="grid gap-3 lg:grid-cols-3">
            <div>
              <div className="mb-2 text-xs text-slate-500">원본 DICOM</div>
              <DicomCanvasViewer dicomPath={originalDicomPath} windowPreset="chest" />
            </div>
            <div>
              <div className="mb-2 flex items-center justify-between gap-2 text-xs text-slate-500">
                <span>오버레이 PNG</span>
                <span>{overlayOpacity}%</span>
              </div>
              <div className="relative aspect-square overflow-hidden rounded-md border border-line bg-black">
                <DicomCanvasViewer dicomPath={originalDicomPath} windowPreset="chest" />
                {done && (
                  <img src={assetUrl(`/api/ai/jobs/${job.id}/overlay.png`)} alt="AI 오버레이" className="absolute inset-0 h-full w-full object-contain" style={{ opacity: overlayOpacity / 100 }} />
                )}
              </div>
              <input className="mt-2 w-full" type="range" min="0" max="100" value={overlayOpacity} onChange={(event) => setOverlayOpacity(Number(event.target.value))} />
            </div>
            <div>
              <div className="mb-2 text-xs text-slate-500">생성 결과 DICOM</div>
              {done ? (
                <DicomCanvasViewer dicomPath={resultDicomPath} windowPreset="auto" />
              ) : (
                <div className="flex aspect-square items-center justify-center rounded-md border border-line bg-black text-sm text-slate-500">결과 DICOM 대기 중</div>
              )}
            </div>
          </div>
        </Card>

        <div className="space-y-4">
          <Card>
            {/* read-back 상태까지 같이 보여줘 STOW 요청 성공과 실제 PACS 저장 성공을 구분한다. */}
            <div className="mb-3 text-sm font-medium">AI 요약</div>
            <div className="grid gap-3">
              <div className="rounded-md border border-line bg-[#0b1322] p-3">
                <div className="text-xs text-slate-500">모델 엔진</div>
                <div className="mt-1"><StatusBadge value={job.modelProvider || "PENDING"} /></div>
              </div>
              <div className="rounded-md border border-line bg-[#0b1322] p-3">
                <div className="text-xs text-slate-500">소견 라벨</div>
                <div className="mt-1 text-lg font-semibold">{displayFindingLabel(job.findingLabel)}</div>
              </div>
              <div className="rounded-md border border-line bg-[#0b1322] p-3">
                <div className="text-xs text-slate-500">점수</div>
                <div className="mt-1 text-3xl font-semibold text-cyanSoft">{typeof job.score === "number" ? job.score.toFixed(3) : "--"}</div>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div className="rounded-md border border-line bg-[#0b1322] p-3">
                  <div className="text-xs text-slate-500">STOW</div>
                  <div className="mt-1"><StatusBadge value={job.stowStatus || "PENDING"} /></div>
                </div>
                <div className="rounded-md border border-line bg-[#0b1322] p-3">
                  <div className="text-xs text-slate-500">Read-back</div>
                  <div className="mt-1"><StatusBadge value={job.readbackStatus || "PENDING"} /></div>
                </div>
              </div>
            </div>
          </Card>

          <Card>
            <div className="mb-3 flex items-center justify-between gap-2 text-sm font-medium">
              <span className="flex items-center gap-2"><Flame size={18} className="text-amberSoft" /> 히트맵</span>
              <label className="text-xs text-slate-400"><input type="checkbox" checked={showHeatmap} onChange={(event) => setShowHeatmap(event.target.checked)} className="mr-2" />표시</label>
            </div>
            {done && showHeatmap ? (
              <img src={assetUrl(`/api/ai/jobs/${job.id}/heatmap.png`)} alt="AI 히트맵" className="aspect-square w-full rounded-md border border-line object-contain" />
            ) : (
              <div className="flex aspect-square items-center justify-center rounded-md border border-line text-sm text-slate-500">히트맵 대기 중</div>
            )}
          </Card>
        </div>
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-3">
        <Card>
          {/* 새 UID는 원본 영상을 덮어쓰지 않고 AI 결과를 별도 series/instance로 보관하기 위한 값이다. */}
          <div className="mb-3 text-sm font-medium">생성된 DICOM UID</div>
          {[job.resultSeriesInstanceUid, job.resultSopInstanceUid].filter(Boolean).map((uid) => (
            <div key={uid} className="mb-2 flex items-center justify-between gap-2 rounded-md border border-line bg-[#0b1322] p-2 text-xs">
              <span className="truncate font-mono text-slate-300">{uid}</span>
              <CopyButton value={uid || ""} />
            </div>
          ))}
          <div className="mt-3"><StatusBadge value={job.stowStatus || "PENDING"} /></div>
          <div className="mt-2"><StatusBadge value={job.readbackStatus || "PENDING"} /></div>
          {job.readbackVerifiedAt && <div className="mt-2 text-xs text-slate-500">검증 시각 {new Date(job.readbackVerifiedAt).toLocaleString("ko-KR")}</div>}
          {job.readbackErrorMessage && <div className="mt-2 text-xs text-rose-100">{job.readbackErrorMessage}</div>}
          {done && (
            <div className="mt-3 flex flex-wrap gap-2">
              <a href={assetUrl(`/api/ai/jobs/${job.id}/overlay.png`)} className="inline-flex items-center gap-2 rounded-md border border-line px-3 py-2 text-sm hover:bg-slate-800"><Download size={16} /> 오버레이 PNG</a>
              <a href={assetUrl(`/api/ai/jobs/${job.id}/heatmap.png`)} className="inline-flex items-center gap-2 rounded-md border border-line px-3 py-2 text-sm hover:bg-slate-800"><Download size={16} /> 히트맵 PNG</a>
              <a href={assetUrl(`/api/ai/jobs/${job.id}/result-dicom`)} className="inline-flex items-center gap-2 rounded-md border border-line px-3 py-2 text-sm hover:bg-slate-800"><Download size={16} /> 결과 DICOM</a>
              {job.resultSopInstanceUid && <CopyButton value={job.resultSopInstanceUid} />}
              <a href="http://localhost:8042" target="_blank" rel="noreferrer" className="inline-flex items-center gap-2 rounded-md border border-line px-3 py-2 text-sm hover:bg-slate-800">Orthanc 열기</a>
            </div>
          )}
        </Card>
        <Card>
          <div className="mb-3 text-sm font-medium">전처리</div>
          <div className="space-y-2 text-xs">
            {Object.entries(preprocessing).map(([key, value]) => (
              <div key={key} className="flex justify-between rounded-md border border-line bg-[#0b1322] p-2">
                <span className="text-slate-500">{preprocessingLabels[key] || key}</span>
                <span>{String(value)}</span>
              </div>
            ))}
          </div>
        </Card>
        <Card>
          <div className="mb-3 text-sm font-medium">QC 요약</div>
          <StatusBadge value={job.qcStatus || qc?.status || "PENDING"} />
          <div className="mt-3 max-h-48 space-y-2 overflow-auto text-xs">
            {(qc?.checks || []).slice(0, 8).map((check: any) => (
              <div key={check.name} className="rounded-md border border-line bg-[#0b1322] p-2">
                <div className="flex justify-between gap-2">
                  <span>{check.name}</span>
                  <span className={check.passed ? "text-cyanSoft" : "text-roseSoft"}>{check.passed ? "통과" : displayStatus(check.severity)}</span>
                </div>
              </div>
            ))}
          </div>
        </Card>
      </div>

      <Card className="mt-4">
        <div className="mb-3 flex items-center justify-between gap-2 text-sm font-medium">
          <span>경계 상자</span>
          <label className="text-xs text-slate-400"><input type="checkbox" checked={showBoxes} onChange={(event) => setShowBoxes(event.target.checked)} className="mr-2" />표시</label>
        </div>
        {!showBoxes ? (
          <div className="text-sm text-slate-500">경계 상자 목록을 숨겼습니다.</div>
        ) : boxes.length === 0 ? (
          <div className="text-sm text-slate-500">이 결과에는 데모 경계 상자가 생성되지 않았습니다.</div>
        ) : (
          <div className="grid gap-2 md:grid-cols-2">
            {boxes.map((box, index) => (
              <div key={index} className="rounded-md border border-line bg-[#0b1322] p-3 text-sm">
                {displayFindingLabel(box.label)} / x {box.x}, y {box.y}, w {box.width}, h {box.height} / {Number(box.score).toFixed(3)}
              </div>
            ))}
          </div>
        )}
      </Card>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <Card>
          {/* timeline은 데모 심사에서 QC, 추론, STOW, read-back 순서를 증명하는 감사용 흐름이다. */}
          <div className="mb-3 text-sm font-medium">AI 작업 타임라인</div>
          <div className="space-y-2">
            {(job.timeline || []).map((event) => (
              <div key={event.id} className="grid gap-2 rounded-md border border-line bg-[#0b1322] p-3 text-sm sm:grid-cols-[150px_1fr_auto]">
                <div className="text-xs text-slate-500">{new Date(event.createdAt).toLocaleString("ko-KR")}</div>
                <div>
                  <div className="font-medium">{event.eventType}</div>
                  <div className="mt-1 text-xs text-slate-400">{event.message}</div>
                </div>
                <StatusBadge value={event.status} />
              </div>
            ))}
          </div>
        </Card>
        <Card>
          <div className="mb-3 text-sm font-medium">결과 DICOM 메타데이터</div>
          {metadata ? (
            <div className="space-y-2 text-xs">
              {Object.entries(metadata).map(([key, value]) => (
                <div key={key} className="flex justify-between gap-3 rounded-md border border-line bg-[#0b1322] p-2">
                  <span className="text-slate-500">{key}</span>
                  <span className="max-w-[65%] truncate font-mono text-slate-300">{String(value || "")}</span>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-sm text-slate-500">결과 DICOM 메타데이터 대기 중</div>
          )}
        </Card>
      </div>
    </>
  );
}
