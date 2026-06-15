"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Search } from "lucide-react";
import { apiFetch, StudySummary } from "@/lib/api";
import { Card, PageTitle, StateMessage, StatusBadge, displayStatus } from "@/components/ui";

export default function StudiesPage() {
  const [studies, setStudies] = useState<StudySummary[]>([]);
  const [query, setQuery] = useState("");
  const [modality, setModality] = useState("ALL");
  const [aiStatus, setAiStatus] = useState("ALL");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    apiFetch<StudySummary[]>("/api/studies")
      .then(setStudies)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  const filtered = useMemo(() => {
    return studies.filter((study) => {
      const text = `${study.patientId} ${study.studyDescription} ${study.studyInstanceUid}`.toLowerCase();
      return (
        text.includes(query.toLowerCase()) &&
        (modality === "ALL" || study.modality === modality) &&
        (aiStatus === "ALL" || study.aiStatus === aiStatus)
      );
    });
  }, [studies, query, modality, aiStatus]);

  const modalities = Array.from(new Set(studies.map((study) => study.modality).filter(Boolean)));
  const aiStatuses = Array.from(new Set(studies.map((study) => study.aiStatus).filter(Boolean)));

  if (loading) return <StateMessage type="loading" title="DICOM 검사를 불러오는 중" detail="QIDO-RS로 Orthanc를 조회하고 있습니다." />;
  if (error) return <StateMessage type="error" title="검사 목록을 불러오지 못했습니다" detail={error} />;

  return (
    <>
      <PageTitle title="검사 목록" subtitle="Orthanc DICOMweb QIDO-RS에서 조회한 합성 데모 검사만 표시합니다." />
      <Card className="mb-4">
        <div className="grid gap-3 md:grid-cols-[1fr_160px_180px]">
          <label className="relative">
            <Search className="absolute left-3 top-3 text-slate-500" size={16} />
            <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="환자 ID, 설명, UID 검색" className="w-full rounded-md border border-line bg-[#0b1322] py-2 pl-9 pr-3 outline-none focus:border-cyanSoft" />
          </label>
          <select value={modality} onChange={(event) => setModality(event.target.value)} className="rounded-md border border-line bg-[#0b1322] px-3 py-2 outline-none">
            <option value="ALL">전체 모달리티</option>
            {modalities.map((value) => <option key={value}>{value}</option>)}
          </select>
          <select value={aiStatus} onChange={(event) => setAiStatus(event.target.value)} className="rounded-md border border-line bg-[#0b1322] px-3 py-2 outline-none">
            <option value="ALL">전체 AI 상태</option>
            {aiStatuses.map((value) => <option key={value} value={value}>{displayStatus(value)}</option>)}
          </select>
        </div>
      </Card>
      {filtered.length === 0 ? (
        <StateMessage type="empty" title="검사를 찾지 못했습니다" detail="시드 데이터를 만들거나 필터를 조정해 주세요." />
      ) : (
        <div className="overflow-hidden rounded-md border border-line">
          <table className="w-full min-w-[980px] border-collapse text-sm">
            <thead className="bg-[#0b1322] text-left text-xs uppercase text-slate-500">
              <tr>
                <th className="px-3 py-3">환자 ID</th>
                <th className="px-3 py-3">검사일</th>
                <th className="px-3 py-3">모달리티</th>
                <th className="px-3 py-3">설명</th>
                <th className="px-3 py-3">Study Instance UID</th>
                <th className="px-3 py-3">시리즈</th>
                <th className="px-3 py-3">AI</th>
                <th className="px-3 py-3">QC</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((study) => (
                <tr key={study.studyInstanceUid} className="border-t border-line bg-panel hover:bg-panel2">
                  <td className="px-3 py-3 font-medium text-cyanSoft">
                    <Link href={`/studies/${study.studyInstanceUid}`}>{study.patientId || "ANON"}</Link>
                  </td>
                  <td className="px-3 py-3 text-slate-300">{study.studyDate || "합성 데이터"}</td>
                  <td className="px-3 py-3">{study.modality || "DX"}</td>
                  <td className="px-3 py-3">{study.studyDescription || "선택 태그 없음"}</td>
                  <td className="max-w-xs truncate px-3 py-3 font-mono text-xs text-slate-400">{study.studyInstanceUid}</td>
                  <td className="px-3 py-3">{study.numberOfSeries || "1"}</td>
                  <td className="px-3 py-3"><StatusBadge value={study.aiStatus} /></td>
                  <td className="px-3 py-3"><StatusBadge value={study.qcStatus} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
