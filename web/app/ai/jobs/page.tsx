"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AiJob, apiFetch } from "@/lib/api";
import { PageTitle, StateMessage, StatusBadge, displayFindingLabel } from "@/components/ui";

export default function AiJobsPage() {
  const [jobs, setJobs] = useState<AiJob[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    apiFetch<AiJob[]>("/api/ai/jobs").then(setJobs).catch((err) => setError(err.message)).finally(() => setLoading(false));
  }, []);

  if (loading) return <StateMessage type="loading" title="AI 작업을 불러오는 중" detail="최근 게이트웨이 AI 작업 기록을 읽고 있습니다." />;
  if (error) return <StateMessage type="error" title="AI 작업을 불러오지 못했습니다" detail={error} />;

  return (
    <>
      <PageTitle title="AI 작업" subtitle="최근 데모 추론 작업, QC 상태, STOW-RS 저장 결과를 보여줍니다." />
      {jobs.length === 0 ? (
        <StateMessage type="empty" title="아직 AI 작업이 없습니다" detail="검사를 열고 합성 DICOM 인스턴스에서 AI 분석을 실행해 주세요." />
      ) : (
        <div className="overflow-hidden rounded-md border border-line">
          <table className="w-full min-w-[920px] border-collapse text-sm">
            <thead className="bg-[#0b1322] text-left text-xs uppercase text-slate-500">
              <tr>
                <th className="px-3 py-3">작업</th>
                <th className="px-3 py-3">상태</th>
                <th className="px-3 py-3">엔진</th>
                <th className="px-3 py-3">소견</th>
                <th className="px-3 py-3">점수</th>
                <th className="px-3 py-3">QC</th>
                <th className="px-3 py-3">STOW</th>
                <th className="px-3 py-3">Read-back</th>
                <th className="px-3 py-3">생성 시각</th>
              </tr>
            </thead>
            <tbody>
              {jobs.map((job) => (
                <tr key={job.id} className="border-t border-line bg-panel hover:bg-panel2">
                  <td className="px-3 py-3 font-mono text-xs text-cyanSoft"><Link href={`/ai/jobs/${job.id}`}>{job.id}</Link></td>
                  <td className="px-3 py-3"><StatusBadge value={job.status} /></td>
                  <td className="px-3 py-3"><StatusBadge value={job.modelProvider || "PENDING"} /></td>
                  <td className="px-3 py-3">{job.findingLabel ? displayFindingLabel(job.findingLabel) : "--"}</td>
                  <td className="px-3 py-3">{typeof job.score === "number" ? job.score.toFixed(3) : "--"}</td>
                  <td className="px-3 py-3"><StatusBadge value={job.qcStatus || "PENDING"} /></td>
                  <td className="px-3 py-3"><StatusBadge value={job.stowStatus || "PENDING"} /></td>
                  <td className="px-3 py-3"><StatusBadge value={job.readbackStatus || "PENDING"} /></td>
                  <td className="px-3 py-3 text-slate-400">{new Date(job.createdAt).toLocaleString("ko-KR")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
