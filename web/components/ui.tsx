import { CheckCircle2, CircleAlert, CircleX, Copy } from "lucide-react";

const statusLabels: Record<string, string> = {
  UNKNOWN: "알 수 없음",
  NOT_RUN: "미실행",
  PENDING: "대기",
  QUEUED: "대기열",
  RUNNING: "실행 중",
  COMPLETED: "완료",
  COMPLETED_VERIFIED: "검증 완료",
  COMPLETED_UNVERIFIED: "검증 미완료",
  FAILED: "실패",
  BLOCKED_BY_QC: "QC 차단",
  PASS: "통과",
  WARN: "경고",
  ERROR: "오류",
  INFO: "정보",
  UP: "정상",
  DOWN: "중단",
  STARTED: "시작",
  SUCCESS: "성공",
  STORED: "저장됨",
  STOW_RS_STORED: "STOW-RS 저장됨",
  READBACK_VERIFIED: "Read-back 확인",
  READBACK_FAILED: "Read-back 실패",
  DEMO_FALLBACK: "데모 엔진",
  ONNX: "ONNX",
  AUTO: "자동",
  EXPERIMENTAL_ANTHROPIC: "실험적 Claude",
  ANTHROPIC: "Claude"
};

const findingLabels: Record<string, string> = {
  "Opacity demo": "음영 의심 데모",
  "Low confidence demo": "낮은 신뢰도 데모",
  "No acute finding demo": "급성 소견 없음 데모"
};

export function displayStatus(value?: string): string {
  const normalized = value || "UNKNOWN";
  if (normalized.startsWith("FAIL ")) {
    return `실패 ${displayStatus(normalized.slice(5))}`;
  }
  if (normalized.startsWith("UP:")) {
    return `정상: ${normalized.slice(3)}`;
  }
  return statusLabels[normalized] || normalized;
}

export function displayFindingLabel(value?: string): string {
  if (!value) return "대기 중";
  return findingLabels[value] || value;
}

export function PageTitle({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <div className="mb-5">
      <h1 className="text-2xl font-semibold text-slate-50">{title}</h1>
      {subtitle && <p className="mt-1 max-w-3xl text-sm text-slate-400">{subtitle}</p>}
    </div>
  );
}

export function Card({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return <section className={`medical-card p-4 ${className}`}>{children}</section>;
}

export function StatusBadge({ value }: { value?: string }) {
  const normalized = value || "UNKNOWN";
  const color =
    normalized.includes("FAIL") || normalized.includes("DOWN") || normalized.includes("BLOCKED")
      ? "border-roseSoft/50 bg-rose-950/30 text-rose-100"
      : normalized.includes("WARN") || normalized.includes("PENDING") || normalized.includes("QUEUED") || normalized.includes("RUNNING")
        ? "border-amberSoft/50 bg-amber-950/30 text-amber-100"
        : normalized.includes("PASS") || normalized.includes("UP") || normalized.includes("COMPLETED") || normalized.includes("STORED")
          ? "border-cyanSoft/50 bg-teal-950/30 text-cyan-100"
          : "border-line bg-slate-900 text-slate-300";
  return <span className={`status-pill ${color}`}>{displayStatus(normalized)}</span>;
}

export function StateMessage({ type, title, detail }: { type: "loading" | "empty" | "error"; title: string; detail?: string }) {
  const Icon = type === "error" ? CircleX : type === "empty" ? CircleAlert : CheckCircle2;
  return (
    <div className="medical-card flex items-start gap-3 p-4 text-sm text-slate-300">
      <Icon className={type === "error" ? "text-roseSoft" : "text-blueSoft"} size={20} />
      <div>
        <div className="font-medium text-slate-100">{title}</div>
        {detail && <div className="mt-1 text-slate-400">{detail}</div>}
      </div>
    </div>
  );
}

export function CopyButton({ value }: { value: string }) {
  return (
    <button
      title="UID 복사"
      className="rounded-md border border-line p-1.5 text-slate-300 hover:bg-slate-800"
      onClick={() => navigator.clipboard.writeText(value)}
    >
      <Copy size={14} />
    </button>
  );
}

export function Disclaimer() {
  return (
    <div className="rounded-md border border-amberSoft/40 bg-amber-950/20 px-3 py-2 text-sm text-amber-100">
      <div>데모 전용입니다. 임상 진료에 사용하지 마세요. 실제 환자 데이터는 없습니다.</div>
      <div className="mt-1 text-xs text-amber-200/80">Demo only. Not for clinical use. No real patient data.</div>
    </div>
  );
}
