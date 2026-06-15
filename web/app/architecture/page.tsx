"use client";

import { useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";
import { Card, Disclaimer, PageTitle, StateMessage } from "@/components/ui";

type Runtime = {
  disclaimer: string;
  dicomwebRoot: string;
  mermaid: string;
  sequenceMermaid?: string;
  terms: Record<string, string>;
};

export default function ArchitecturePage() {
  const [runtime, setRuntime] = useState<Runtime | null>(null);
  const [svg, setSvg] = useState("");
  const [sequenceSvg, setSequenceSvg] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    apiFetch<Runtime>("/api/architecture/runtime").then(setRuntime).catch((err) => setError(err.message));
  }, []);

  useEffect(() => {
    if (!runtime?.mermaid) return;
    import("mermaid").then(async (module) => {
      const mermaid = module.default;
      mermaid.initialize({ startOnLoad: false, theme: "dark", securityLevel: "strict" });
      const result = await mermaid.render("hanul-architecture", runtime.mermaid);
      setSvg(result.svg);
      if (runtime.sequenceMermaid) {
        const sequence = await mermaid.render("hanul-sequence", runtime.sequenceMermaid);
        setSequenceSvg(sequence.svg);
      }
    }).catch(() => setSvg(""));
  }, [runtime]);

  if (error) return <StateMessage type="error" title="아키텍처를 불러오지 못했습니다" detail={error} />;
  if (!runtime) return <StateMessage type="loading" title="런타임 아키텍처를 불러오는 중" detail="게이트웨이 아키텍처 엔드포인트를 읽고 있습니다." />;

  return (
    <>
      <PageTitle title="아키텍처" subtitle="브라우저, 게이트웨이, PACS, AI 서비스, 생성 DICOM, 감사 데이터베이스까지 이어지는 DICOMweb 흐름입니다." />
      <div className="mb-4"><Disclaimer /></div>
      <div className="grid gap-4 xl:grid-cols-[1.25fr_0.75fr]">
        <Card>
          <div className="mb-3 text-sm font-medium">Mermaid 런타임 다이어그램</div>
          {svg ? (
            <div className="overflow-auto rounded-md border border-line bg-[#0b1322] p-4" dangerouslySetInnerHTML={{ __html: svg }} />
          ) : (
            <pre className="overflow-auto rounded-md border border-line bg-[#0b1322] p-4 text-sm text-slate-300">{runtime.mermaid}</pre>
          )}
        </Card>
        <div className="space-y-4">
          <Card>
            <div className="mb-3 text-sm font-medium">DICOMweb 루트</div>
            <div className="rounded-md border border-line bg-[#0b1322] p-3 font-mono text-sm">{runtime.dicomwebRoot}</div>
          </Card>
          <Card>
            <div className="mb-3 text-sm font-medium">프로토콜 설명</div>
            <div className="space-y-3 text-sm text-slate-300">
              {Object.entries(runtime.terms).map(([term, description]) => (
                <div key={term} className="rounded-md border border-line bg-[#0b1322] p-3">
                  <div className="font-semibold text-cyanSoft">{term}</div>
                  <div className="mt-1 text-slate-400">{description}</div>
                </div>
              ))}
            </div>
          </Card>
        </div>
      </div>
      <Card className="mt-4">
        <div className="mb-3 text-sm font-medium">AI 작업 Sequence Diagram</div>
        {sequenceSvg ? (
          <div className="overflow-auto rounded-md border border-line bg-[#0b1322] p-4" dangerouslySetInnerHTML={{ __html: sequenceSvg }} />
        ) : (
          <pre className="overflow-auto rounded-md border border-line bg-[#0b1322] p-4 text-sm text-slate-300">{runtime.sequenceMermaid}</pre>
        )}
      </Card>
    </>
  );
}
