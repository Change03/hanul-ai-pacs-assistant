export const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers || {});
  if (options.body && !(options.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
    credentials: "include"
  });
  if (!response.ok) {
    let message = `${response.status} ${response.statusText}`;
    try {
      const body = await response.json();
      message = body.message || message;
    } catch {
      // Leave default message.
    }
    throw new Error(message);
  }
  return response.json();
}

export function assetUrl(path: string): string {
  return `${API_BASE}${path}`;
}

export type StudySummary = {
  patientId: string;
  studyDate: string;
  modality: string;
  studyDescription: string;
  studyInstanceUid: string;
  numberOfSeries: string;
  aiStatus: string;
  qcStatus: string;
};

export type InstanceSummary = {
  studyInstanceUid: string;
  seriesInstanceUid: string;
  sopInstanceUid: string;
  sopClassUid: string;
  instanceNumber: string;
};

export type QcReport = {
  id?: string;
  status: "PASS" | "WARN" | "FAIL";
  checks: {
    category?: string;
    name: string;
    severity: "INFO" | "WARN" | "ERROR";
    passed: boolean;
    message: string;
    observed?: string;
    expectedHint?: string;
    suggestedFix: string;
  }[];
  createdAt?: string;
};

export type AiJobEvent = {
  id: string;
  eventType: string;
  status: string;
  message: string;
  details: Record<string, any>;
  createdAt: string;
};

export type AiJob = {
  id: string;
  status: string;
  studyInstanceUid: string;
  seriesInstanceUid: string;
  sopInstanceUid: string;
  resultSeriesInstanceUid?: string;
  resultSopInstanceUid?: string;
  modelProvider?: string;
  findingLabel?: string;
  score?: number;
  qcStatus?: string;
  stowStatus?: string;
  readbackStatus?: string;
  readbackVerifiedAt?: string;
  readbackErrorMessage?: string;
  errorMessage?: string;
  result?: Record<string, any>;
  timeline?: AiJobEvent[];
  disclaimer?: string;
  clinicalUseAllowed?: boolean;
  syntheticOnly?: boolean;
  createdAt: string;
  updatedAt: string;
};

export type ResultDicomMetadata = {
  studyInstanceUid?: string;
  seriesInstanceUid?: string;
  sopInstanceUid?: string;
  sopClassUid?: string;
  modality?: string;
  seriesDescription?: string;
  imageComments?: string;
  rows?: string;
  columns?: string;
  transferSyntaxUid?: string;
};
