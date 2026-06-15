"use client";

import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";
import { PageTitle, StateMessage, StatusBadge } from "@/components/ui";

type Audit = {
  id: string;
  actor: string;
  action: string;
  resourceType: string;
  resourceId: string;
  outcome: string;
  details: Record<string, any>;
  createdAt: string;
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

export default function AuditPage() {
  const [audit, setAudit] = useState<Audit[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    apiFetch<Audit[]>("/api/audit").then(setAudit).catch((err) => setError(err.message)).finally(() => setLoading(false));
  }, []);

  if (loading) return <StateMessage type="loading" title="감사 로그를 불러오는 중" detail="최근 게이트웨이 감사 기록을 조회하고 있습니다." />;
  if (error) return <StateMessage type="error" title="감사 로그를 불러오지 못했습니다" detail={error} />;

  return (
    <>
      <PageTitle title="감사 로그" subtitle="모든 DICOM 접근, QC 요청, AI 추론, STOW-RS 업로드를 게이트웨이가 기록합니다." />
      {audit.length === 0 ? (
        <StateMessage type="empty" title="감사 기록이 없습니다" detail="검사를 조회하거나 QC를 실행하면 감사 항목이 생성됩니다." />
      ) : (
        <div className="overflow-hidden rounded-md border border-line">
          <table className="w-full min-w-[900px] border-collapse text-sm">
            <thead className="bg-[#0b1322] text-left text-xs uppercase text-slate-500">
              <tr>
                <th className="px-3 py-3">수행자</th>
                <th className="px-3 py-3">동작</th>
                <th className="px-3 py-3">대상</th>
                <th className="px-3 py-3">결과</th>
                <th className="px-3 py-3">시간</th>
                <th className="px-3 py-3">상세</th>
              </tr>
            </thead>
            <tbody>
              {audit.map((event) => (
                <tr key={event.id} className="border-t border-line bg-panel">
                  <td className="px-3 py-3">{event.actor}</td>
                  <td className="px-3 py-3 font-medium">{auditActionLabels[event.action] || event.action}</td>
                  <td className="max-w-xs truncate px-3 py-3 font-mono text-xs text-slate-400">{resourceLabels[event.resourceType] || event.resourceType}:{event.resourceId}</td>
                  <td className="px-3 py-3"><StatusBadge value={event.outcome} /></td>
                  <td className="px-3 py-3 text-slate-400">{new Date(event.createdAt).toLocaleString("ko-KR")}</td>
                  <td className="max-w-xs truncate px-3 py-3 font-mono text-xs text-slate-500">{JSON.stringify(event.details)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
