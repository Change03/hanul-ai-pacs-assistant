package com.hanul.aipacs.service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// QC와 메타데이터 요약을 위한 경량 parser다. 전체 DICOM 표준 구현이 아니라 Explicit VR 데모 입력을 빠르게 검사하는 용도다.
public final class DicomLiteParser {
    private static final Set<String> LONG_VR = Set.of("OB", "OD", "OF", "OL", "OW", "SQ", "UC", "UR", "UT", "UN");

    private DicomLiteParser() {
    }

    public static ParsedDicom parse(byte[] bytes) {
        // Part 10 파일이면 128바이트 preamble 뒤에 DICM 마커가 온다.
        boolean hasPreamble = bytes.length > 132
            && bytes[128] == 'D'
            && bytes[129] == 'I'
            && bytes[130] == 'C'
            && bytes[131] == 'M';
        int offset = hasPreamble ? 132 : 0;
        Map<String, String> values = new HashMap<>();
        boolean pixelData = false;
        long pixelDataLength = -1;
        int privateTagCount = 0;

        // Explicit VR Little Endian의 기본 tag/value layout만 순차적으로 훑는다.
        while (offset + 8 <= bytes.length) {
            int group = ushort(bytes, offset);
            int element = ushort(bytes, offset + 2);
            String tag = "%04X%04X".formatted(group, element);
            String vr = new String(bytes, offset + 4, 2, StandardCharsets.US_ASCII);
            int headerLength;
            long length;
            if (LONG_VR.contains(vr)) {
                if (offset + 12 > bytes.length) {
                    break;
                }
                headerLength = 12;
                length = uint(bytes, offset + 8);
            } else {
                headerLength = 8;
                length = ushort(bytes, offset + 6);
            }

            if ("7FE00010".equals(tag)) {
                pixelData = true;
                pixelDataLength = length == 0xFFFFFFFFL ? -1 : length;
                if (length == 0xFFFFFFFFL) {
                    break;
                }
            }
            if ((group & 1) == 1) {
                privateTagCount++;
            }

            // 길이가 비정상이면 손상되었거나 지원 범위를 벗어난 입력으로 보고 더 진행하지 않는다.
            long valueStart = offset + (long) headerLength;
            if (length == 0xFFFFFFFFL || valueStart + length > bytes.length) {
                break;
            }
            if (isTextVr(vr) || tag.endsWith("000D") || tag.endsWith("000E") || tag.endsWith("0018")) {
                String value = new String(bytes, (int) valueStart, (int) length, StandardCharsets.ISO_8859_1)
                    .replace("\0", "")
                    .trim();
                if (!value.isBlank()) {
                    values.put(tag, value);
                }
            } else if ("US".equals(vr) && length >= 2 && isUsefulUnsignedShort(tag)) {
                values.put(tag, String.valueOf(ushort(bytes, (int) valueStart)));
            }
            offset = (int) (valueStart + length + (length % 2));
        }

        // 일부 파일은 순차 파싱 중 필요한 태그를 놓칠 수 있어, 주요 태그만 한 번 더 보수적으로 검색한다.
        for (String tag : Set.of(
            "00020010",
            "00080016",
            "00080018",
            "00080060",
            "00080080",
            "00080090",
            "00081030",
            "0008103E",
            "00100010",
            "00100020",
            "0020000D",
            "0020000E",
            "00280002",
            "00280004",
            "00280010",
            "00280011",
            "00280100",
            "00280101",
            "00280301",
            "00204000"
        )) {
            values.putIfAbsent(tag, findFallbackValue(bytes, tag));
        }
        pixelData = pixelData || containsTag(bytes, 0x7FE0, 0x0010);
        return new ParsedDicom(hasPreamble, values, pixelData, pixelDataLength, privateTagCount, bytes.length);
    }

    private static boolean isTextVr(String vr) {
        return Set.of("AE", "AS", "CS", "DA", "DS", "DT", "IS", "LO", "LT", "PN", "SH", "ST", "TM", "UC", "UI", "UR", "UT").contains(vr);
    }

    private static boolean isUsefulUnsignedShort(String tag) {
        return Set.of("00280002", "00280010", "00280011", "00280100", "00280101", "00280102", "00280103").contains(tag);
    }

    private static int ushort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static long uint(byte[] bytes, int offset) {
        return ((long) bytes[offset] & 0xff)
            | (((long) bytes[offset + 1] & 0xff) << 8)
            | (((long) bytes[offset + 2] & 0xff) << 16)
            | (((long) bytes[offset + 3] & 0xff) << 24);
    }

    private static boolean containsTag(byte[] bytes, int group, int element) {
        byte[] needle = tagBytes(group, element);
        return findSequence(bytes, needle, 0) >= 0;
    }

    private static String findFallbackValue(byte[] bytes, String tag) {
        // QC 화면에 보여줄 핵심 텍스트 태그만 복구하기 위한 fallback 검색이다.
        int group = Integer.parseInt(tag.substring(0, 4), 16);
        int element = Integer.parseInt(tag.substring(4), 16);
        byte[] needle = tagBytes(group, element);
        int offset = bytes.length > 132 ? 132 : 0;
        while (offset >= 0 && offset + 8 <= bytes.length) {
            int found = findSequence(bytes, needle, offset);
            if (found < 0 || found + 8 > bytes.length) {
                return "";
            }
            String vr = new String(bytes, found + 4, 2, StandardCharsets.US_ASCII);
            boolean explicitVr = isExplicitVr(vr);
            int headerLength = explicitVr && LONG_VR.contains(vr) ? 12 : 8;
            long length = explicitVr
                ? LONG_VR.contains(vr) ? uint(bytes, found + 8) : ushort(bytes, found + 6)
                : uint(bytes, found + 4);
            long valueStart = found + (long) headerLength;
            if (explicitVr && "US".equals(vr) && length >= 2 && valueStart + 2 <= bytes.length && isUsefulUnsignedShort(tag)) {
                return String.valueOf(ushort(bytes, (int) valueStart));
            }
            if (length > 0 && length < 1024 && valueStart + length <= bytes.length) {
                String value = new String(bytes, (int) valueStart, (int) length, StandardCharsets.ISO_8859_1)
                    .replace("\0", "")
                    .trim();
                if (looksLikeText(value)) {
                    return value;
                }
            }
            offset = found + 1;
        }
        return "";
    }

    private static boolean isExplicitVr(String vr) {
        if (vr.length() != 2) {
            return false;
        }
        return Character.isUpperCase(vr.charAt(0)) && Character.isUpperCase(vr.charAt(1));
    }

    private static boolean looksLikeText(String value) {
        if (value.isBlank()) {
            return false;
        }
        int printable = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if ((ch >= 32 && ch < 127) || ch == '\t') {
                printable++;
            }
        }
        return printable >= Math.max(1, value.length() * 3 / 4);
    }

    private static byte[] tagBytes(int group, int element) {
        return new byte[] {
            (byte) (group & 0xff),
            (byte) ((group >> 8) & 0xff),
            (byte) (element & 0xff),
            (byte) ((element >> 8) & 0xff)
        };
    }

    private static int findSequence(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = Math.max(0, from); i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public record ParsedDicom(
        boolean hasPreamble,
        Map<String, String> values,
        boolean hasPixelData,
        long pixelDataLength,
        int privateTagCount,
        int byteLength
    ) {
        public String value(String tag) {
            return values.get(tag);
        }

        public Map<String, Object> metadata() {
            // 프론트 상세 화면에서 바로 표시하기 쉬운 이름으로 태그를 정리한다.
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("PatientID", value("00100020"));
            metadata.put("PatientName", value("00100010"));
            metadata.put("StudyInstanceUID", value("0020000D"));
            metadata.put("SeriesInstanceUID", value("0020000E"));
            metadata.put("SOPInstanceUID", value("00080018"));
            metadata.put("SOPClassUID", value("00080016"));
            metadata.put("Modality", value("00080060"));
            metadata.put("TransferSyntaxUID", value("00020010"));
            metadata.put("Rows", value("00280010"));
            metadata.put("Columns", value("00280011"));
            metadata.put("SamplesPerPixel", value("00280002"));
            metadata.put("BitsAllocated", value("00280100"));
            metadata.put("BitsStored", value("00280101"));
            metadata.put("PhotometricInterpretation", value("00280004"));
            metadata.put("StudyDescription", value("00081030"));
            metadata.put("SeriesDescription", value("0008103E"));
            metadata.put("ReferringPhysicianName", value("00080090"));
            metadata.put("BurnedInAnnotation", value("00280301"));
            metadata.put("ImageComments", value("00204000"));
            metadata.put("hasPixelData", hasPixelData);
            metadata.put("pixelDataLength", pixelDataLength);
            metadata.put("privateTagCount", privateTagCount);
            metadata.put("hasDicmPreamble", hasPreamble);
            metadata.put("byteLength", byteLength);
            return metadata;
        }
    }
}
