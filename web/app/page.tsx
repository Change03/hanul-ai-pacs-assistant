"use client";

import { useEffect, useState } from "react";
import { BrainCircuit, ClipboardCheck, Database, FileWarning, Server, ShieldCheck } from "lucide-react";
import { apiFetch } from "@/lib/api";
import { Card, PageTitle, StateMessage, StatusBadge } from "@/components/ui";

type Dashboard = {
  studies: number;
  aiJobsCompleted: number;
  qcWarnings: number;
  failedJobs: number;
  health: Record<string, string>;
  recentAudit: { id: string; actor: string; action: string; resourceType: string; outcome: string; createdAt: string }[];
};

const cards = [
  { key: "studies", label: "검사 수", icon: Database },
  { key: "aiJobsCompleted", label: "완료된 AI 작업", icon: BrainCircuit },
  { key: "qcWarnings", label: "QC 경고", icon: ClipboardCheck },
  { key: "failedJobs", label: "실패한 작업", icon: FileWarning }
] as const;

const healthLabels: Record<string, string> = {
  orthanc: "Orthanc PACS",
  ai: "AI 서비스",
  aiService: "AI 서비스",
  postgres: "PostgreSQL"
};

const auditActionLabels: Record<string, string> = {
  DICOM_QIDO_STUDIES: "검사 목록 조회",
  DICOM_QIDO_SERIES: "시리즈 목록 조회",
  DICOM_QIDO_INSTANCES: "인스턴스 목록 조회",
  DICOM_QIDO_SEARCH: "DICOM 검색",
  DICOM_METADATA: "DICOM 메타데이터 조회",
  DICOM_PREVIEW: "DICOM 미리보기 생성",
  DICOM_WADO_RETRIEVE: "DICOM 객체 조회",
  DICOM_STOW_RESULT: "결과 DICOM 저장",
  ORTHANC_READBACK_VERIFIED: "Orthanc read-back 확인",
  ORTHANC_READBACK_FAILED: "Orthanc read-back 실패",
  QC_VALIDATE: "QC 검증",
  AI_JOB_CREATE: "AI 작업 생성",
  AI_INFERENCE: "AI 추론",
  AI_INFERENCE_BLOCKED: "AI 추론 차단",
  AI_JOB_FAILURE: "AI 작업 실패"
};

const resourceLabels: Record<string, string> = {
  STUDY: "검사",
  SERIES: "시리즈",
  INSTANCE: "인스턴스",
  AI_JOB: "AI 작업"
};

export default function DashboardPage() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    apiFetch<Dashboard>("/api/dashboard").then(setData).catch((err) => setError(err.message));
  }, []);

  if (error) return <StateMessage type="error" title="대시보드를 불러오지 못했습니다" detail={error} />;
  if (!data) return <StateMessage type="loading" title="대시보드를 불러오는 중" detail="Orthanc, AI 서비스, PostgreSQL, 최근 감사 이벤트를 확인하고 있습니다." />;

  return (
    <>
      <PageTitle title="Hanul AI-PACS Assistant" subtitle="QC 게이트, 결정론적 데모 AI 추론, 결과 DICOM 생성, 감사 로그를 포함한 DICOMweb 미니 PACS입니다." />
      <div className="mb-5 grid gap-3 lg:grid-cols-5">
        <Card className="lg:col-span-3">
          <div className="mb-3 flex items-center gap-2 text-cyanSoft"><ShieldCheck size={18} /> 데모 시나리오</div>
          <div className="grid gap-2 text-sm text-slate-300 sm:grid-cols-5">
            {["검사 조회", "QC 실행", "AI 분석", "오버레이 확인", "STOW-RS 확인"].map((step, index) => (
              <div key={step} className="rounded-md border border-line bg-[#0b1322] p-3">
                <div className="text-xs text-slate-500">단계 {index + 1}</div>
                <div className="mt-1 font-medium">{step}</div>
              </div>
            ))}
          </div>
        </Card>
        <Card className="lg:col-span-2">
          <div className="mb-2 text-sm font-medium text-slate-100">왜 중요한가</div>
          <div className="space-y-1 text-sm text-slate-400">
            <div>원본 DICOM 객체는 변경하지 않습니다.</div>
            <div>AI 결과는 새 DICOM 객체로 생성됩니다.</div>
            <div>QC가 안전하지 않거나 잘못된 입력을 차단합니다.</div>
            <div>모든 DICOM 접근과 STOW 업로드를 감사 로그로 남깁니다.</div>
          </div>
        </Card>
      </div>

      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        {cards.map((card) => {
          const Icon = card.icon;
          return (
            <Card key={card.key}>
              <div className="flex items-center justify-between">
                <div>
                  <div className="text-sm text-slate-400">{card.label}</div>
                  <div className="mt-2 text-3xl font-semibold">{data[card.key]}</div>
                </div>
                <div className="rounded-md bg-teal-300/10 p-3 text-cyanSoft"><Icon size={24} /></div>
              </div>
            </Card>
          );
        })}
      </div>

      <div className="mt-5 grid gap-3 lg:grid-cols-2">
        <Card>
          <div className="mb-3 flex items-center gap-2 text-sm font-medium"><Server size={18} className="text-blueSoft" /> 시스템 상태</div>
          <div className="grid gap-2 sm:grid-cols-2">
            {Object.entries(data.health).map(([name, status]) => (
              <div key={name} className="flex items-center justify-between rounded-md border border-line bg-[#0b1322] p-3">
                <span className="text-slate-300">{healthLabels[name] || name}</span>
                <StatusBadge value={status} />
              </div>
            ))}
          </div>
        </Card>
        <Card>
          <div className="mb-3 text-sm font-medium">최근 감사 기록</div>
          {data.recentAudit.length === 0 ? (
            <div className="text-sm text-slate-500">아직 감사 기록이 없습니다.</div>
          ) : (
            <div className="space-y-2">
              {data.recentAudit.map((event) => (
                <div key={event.id} className="flex items-center justify-between gap-3 rounded-md border border-line bg-[#0b1322] p-3 text-sm">
                  <div>
                    <div className="font-medium">{auditActionLabels[event.action] || event.action}</div>
                    <div className="text-xs text-slate-500">{event.actor} / {resourceLabels[event.resourceType] || event.resourceType}</div>
                  </div>
                  <StatusBadge value={event.outcome} />
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>

      <div className="mt-5 flex flex-wrap gap-2 text-xs text-slate-300">
        {["DICOMweb", "Spring Boot", "Orthanc", "ONNX Runtime", "QC Gate", "STOW-RS"].map((badge) => (
          <span key={badge} className="rounded-md border border-line bg-[#0b1322] px-3 py-1">{badge}</span>
        ))}
      </div>
    </>
  );
}
