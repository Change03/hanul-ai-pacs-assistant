"use client";

import { useEffect, useRef, useState } from "react";
import { API_BASE } from "@/lib/api";

type Props = {
  dicomPath: string;
  windowPreset?: string;
  windowCenter?: number;
  windowWidth?: number;
  zoom?: number;
  className?: string;
};

type ParsedImage = {
  rows: number;
  columns: number;
  kind: "grayscale" | "rgb";
  pixels?: Float32Array;
  rgbPixels?: Uint8ClampedArray;
  photometric: string;
  modality: string;
  windowCenter?: number;
  windowWidth?: number;
};

const ctPresets: Record<string, { center: number; width: number }> = {
  // 데모에서 제공하는 기본 WL/WW preset이다. 수동 슬라이더가 있으면 이 값보다 우선한다.
  chest: { center: 40, width: 400 },
  lung: { center: -600, width: 1500 },
  bone: { center: 300, width: 1500 }
};

export function DicomCanvasViewer({ dicomPath, windowPreset = "auto", windowCenter, windowWidth, zoom = 1, className = "" }: Props) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const [status, setStatus] = useState("DICOM 로딩 중");
  const [error, setError] = useState("");
  const [info, setInfo] = useState("");
  const [image, setImage] = useState<ParsedImage | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function render() {
      // Backend가 인증/감사 로그를 처리하므로, 브라우저는 gateway API에서 원본 DICOM bytes를 받아온다.
      setStatus("PACS에서 DICOM 객체를 가져오는 중");
      setError("");
      try {
        const response = await fetch(`${API_BASE}${dicomPath}`, { credentials: "include" });
        if (!response.ok) {
          throw new Error(`${response.status} ${response.statusText}`);
        }
        const bytes = new Uint8Array(await response.arrayBuffer());
        const parsed = await parseDicom(bytes);
        if (cancelled) return;
        setImage(parsed);
        setStatus("");
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "DICOM 렌더링 실패");
          setStatus("");
        }
      }
    }

    if (dicomPath) {
      render();
    }
    return () => {
      cancelled = true;
    };
  }, [dicomPath]);

  useEffect(() => {
    if (!image) return;
    // DICOM은 한 번만 파싱하고, preset/수동 WL/WW 변경은 canvas redraw로만 처리한다.
    drawImage(canvasRef.current, image, { preset: windowPreset, center: windowCenter, width: windowWidth });
    const windowLabel = windowCenter !== undefined && windowWidth !== undefined ? `WC ${windowCenter} / WW ${windowWidth}` : windowPreset;
    setInfo(`${image.columns} x ${image.rows} / ${image.modality || "DICOM"} / ${windowLabel}`);
  }, [image, windowPreset, windowCenter, windowWidth]);

  return (
    <div className={`relative flex aspect-square items-center justify-center overflow-hidden rounded-md border border-line bg-black ${className}`}>
      <canvas ref={canvasRef} className="h-full w-full object-contain transition-transform" style={{ transform: `scale(${zoom})` }} />
      {status && <div className="absolute inset-x-3 top-3 rounded-md bg-slate-950/80 px-3 py-2 text-xs text-slate-300">{status}</div>}
      {error && <div className="absolute inset-x-3 top-3 rounded-md border border-roseSoft/50 bg-rose-950/80 px-3 py-2 text-xs text-rose-100">{error}</div>}
      {info && !error && <div className="absolute bottom-3 left-3 rounded-md bg-slate-950/75 px-2 py-1 text-xs text-slate-300">{info}</div>}
    </div>
  );
}

async function parseDicom(bytes: Uint8Array): Promise<ParsedImage> {
  // 이 컴포넌트는 데모 preview renderer다. 압축/멀티프레임/진단용 도구는 지원 범위 밖이다.
  const dicomParser = await import("dicom-parser");
  const dataSet = dicomParser.parseDicom(bytes);
  const pixelElement = dataSet.elements.x7fe00010;
  if (!pixelElement) {
    throw new Error("DICOM PixelData를 찾을 수 없습니다");
  }

  const rows = dataSet.uint16("x00280010") || 0;
  const columns = dataSet.uint16("x00280011") || 0;
  const samplesPerPixel = dataSet.uint16("x00280002") || 1;
  if (!rows || !columns) {
    throw new Error("DICOM 행/열 정보를 읽을 수 없습니다");
  }
  if (samplesPerPixel !== 1 && samplesPerPixel !== 3) {
    throw new Error("현재 뷰어는 단일 채널 또는 RGB DICOM만 지원합니다");
  }

  const bitsAllocated = dataSet.uint16("x00280100") || 16;
  const bitsStored = dataSet.uint16("x00280101") || bitsAllocated;
  const pixelRepresentation = dataSet.uint16("x00280103") || 0;
  const photometric = dataSet.string("x00280004") || "MONOCHROME2";
  const modality = dataSet.string("x00080060") || "";
  const planarConfiguration = dataSet.uint16("x00280006") || 0;
  const slope = numberValue(dataSet.string("x00281053")) ?? 1;
  const intercept = numberValue(dataSet.string("x00281052")) ?? 0;
  const pixelCount = rows * columns;
  const start = bytes.byteOffset + pixelElement.dataOffset;
  const view = new DataView(bytes.buffer, start, Math.min(pixelElement.length, bytes.byteLength - pixelElement.dataOffset));

  if (samplesPerPixel === 3) {
    // AI 결과 Secondary Capture는 RGB overlay이므로 grayscale과 별도 경로로 처리한다.
    return {
      rows,
      columns,
      kind: "rgb",
      rgbPixels: readRgbPixels(bytes, view, pixelElement.dataOffset, pixelCount, bitsAllocated, bitsStored, planarConfiguration, photometric),
      photometric,
      modality,
      windowCenter: numberValue(dataSet.string("x00281050")),
      windowWidth: numberValue(dataSet.string("x00281051"))
    };
  }

  const pixels = new Float32Array(pixelCount);
  if (bitsAllocated <= 8) {
    for (let i = 0; i < pixelCount; i++) {
      pixels[i] = bytes[pixelElement.dataOffset + i] * slope + intercept;
    }
  } else {
    // 12/16-bit grayscale DICOM은 bitsStored와 signed 여부를 반영해 display pixel로 변환한다.
    const mask = bitsStored >= 32 ? 0xffffffff : (1 << bitsStored) - 1;
    const signBit = 1 << (bitsStored - 1);
    for (let i = 0; i < pixelCount; i++) {
      let value = view.getUint16(i * 2, true) & mask;
      if (pixelRepresentation === 1 && (value & signBit)) {
        value -= 1 << bitsStored;
      }
      pixels[i] = value * slope + intercept;
    }
  }

  return {
    rows,
    columns,
    kind: "grayscale",
    pixels,
    photometric,
    modality,
    windowCenter: numberValue(dataSet.string("x00281050")),
    windowWidth: numberValue(dataSet.string("x00281051"))
  };
}

function drawImage(canvas: HTMLCanvasElement | null, image: ParsedImage, windowing: { preset: string; center?: number; width?: number }) {
  if (!canvas) return;
  canvas.width = image.columns;
  canvas.height = image.rows;
  const context = canvas.getContext("2d");
  if (!context) return;

  const output = context.createImageData(image.columns, image.rows);
  if (image.kind === "rgb" && image.rgbPixels) {
    // RGB DICOM은 이미 display-ready이므로 WL/WW를 적용하지 않는다.
    for (let i = 0; i < image.rgbPixels.length / 3; i++) {
      const source = i * 3;
      const target = i * 4;
      output.data[target] = image.rgbPixels[source];
      output.data[target + 1] = image.rgbPixels[source + 1];
      output.data[target + 2] = image.rgbPixels[source + 2];
      output.data[target + 3] = 255;
    }
    context.putImageData(output, 0, 0);
    return;
  }

  if (!image.pixels) return;
  const { center, width } = windowFor(image, windowing);
  const low = center - width / 2;
  const high = center + width / 2;
  const inverse = image.photometric === "MONOCHROME1";

  for (let i = 0; i < image.pixels.length; i++) {
    const normalized = Math.max(0, Math.min(1, (image.pixels[i] - low) / Math.max(1, high - low)));
    const gray = Math.round((inverse ? 1 - normalized : normalized) * 255);
    const offset = i * 4;
    output.data[offset] = gray;
    output.data[offset + 1] = gray;
    output.data[offset + 2] = gray;
    output.data[offset + 3] = 255;
  }
  context.putImageData(output, 0, 0);
}

function windowFor(image: ParsedImage, windowing: { preset: string; center?: number; width?: number }) {
  if (!image.pixels) {
    return { center: 128, width: 256 };
  }
  if (Number.isFinite(windowing.center) && Number.isFinite(windowing.width) && (windowing.width || 0) > 1) {
    // 사용자가 수동 WL/WW를 조절한 경우 DICOM tag나 preset보다 우선한다.
    return { center: windowing.center as number, width: windowing.width as number };
  }
  const preset = windowing.preset;
  if (image.modality === "CT" && preset !== "auto" && ctPresets[preset]) {
    return ctPresets[preset];
  }
  if (image.windowCenter && image.windowWidth && image.windowWidth > 1) {
    return { center: image.windowCenter, width: image.windowWidth };
  }
  const [low, high] = sampledPercentiles(image.pixels);
  return { center: (low + high) / 2, width: Math.max(1, high - low) };
}

function sampledPercentiles(values: Float32Array) {
  // 큰 이미지를 매번 전체 정렬하지 않도록 일부 샘플만 뽑아 auto window를 계산한다.
  const step = Math.max(1, Math.floor(values.length / 200000));
  const sample: number[] = [];
  for (let i = 0; i < values.length; i += step) {
    sample.push(values[i]);
  }
  sample.sort((a, b) => a - b);
  return [sample[Math.floor(sample.length * 0.01)] ?? 0, sample[Math.floor(sample.length * 0.99)] ?? 255];
}

function readRgbPixels(
  bytes: Uint8Array,
  view: DataView,
  dataOffset: number,
  pixelCount: number,
  bitsAllocated: number,
  bitsStored: number,
  planarConfiguration: number,
  photometric: string
) {
  const output = new Uint8ClampedArray(pixelCount * 3);
  const planar = planarConfiguration === 1;
  const ybr = photometric.startsWith("YBR");
  for (let i = 0; i < pixelCount; i++) {
    const sample = (channel: number) => {
      if (bitsAllocated <= 8) {
        const sampleIndex = planar ? channel * pixelCount + i : i * 3 + channel;
        return bytes[dataOffset + sampleIndex] ?? 0;
      }
      const sampleIndex = planar ? channel * pixelCount + i : i * 3 + channel;
      const raw = view.getUint16(sampleIndex * 2, true);
      const max = Math.max(1, 2 ** Math.min(bitsStored, 16) - 1);
      return Math.round((raw / max) * 255);
    };
    let r = sample(0);
    let g = sample(1);
    let b = sample(2);
    if (ybr) {
      [r, g, b] = ybrToRgb(r, g, b);
    }
    const target = i * 3;
    output[target] = r;
    output[target + 1] = g;
    output[target + 2] = b;
  }
  return output;
}

function ybrToRgb(y: number, cb: number, cr: number): [number, number, number] {
  return [
    clampByte(y + 1.402 * (cr - 128)),
    clampByte(y - 0.344136 * (cb - 128) - 0.714136 * (cr - 128)),
    clampByte(y + 1.772 * (cb - 128))
  ];
}

function clampByte(value: number) {
  return Math.max(0, Math.min(255, Math.round(value)));
}

function numberValue(value?: string): number | undefined {
  if (!value) return undefined;
  const parsed = Number.parseFloat(value.split("\\")[0]);
  return Number.isFinite(parsed) ? parsed : undefined;
}
