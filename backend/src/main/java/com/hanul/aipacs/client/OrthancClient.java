package com.hanul.aipacs.client;

import com.hanul.aipacs.dto.StudyDtos.InstanceSummary;
import com.hanul.aipacs.dto.StudyDtos.SeriesSummary;
import com.hanul.aipacs.dto.StudyDtos.StudySummary;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrthancClient {
    // rest는 Orthanc 관리/파일 API, dicomWeb은 QIDO/STOW 같은 DICOMweb API에 사용한다.
    private final RestClient rest;
    private final RestClient dicomWeb;

    public OrthancClient(
        RestClient.Builder builder,
        @Value("${app.orthanc.base-url}") String baseUrl,
        @Value("${app.orthanc.dicomweb-url}") String dicomWebUrl,
        @Value("${app.orthanc.username}") String username,
        @Value("${app.orthanc.password}") String password
    ) {
        String auth = "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        this.rest = builder.clone().baseUrl(baseUrl).defaultHeader(HttpHeaders.AUTHORIZATION, auth).build();
        this.dicomWeb = builder.clone().baseUrl(dicomWebUrl).defaultHeader(HttpHeaders.AUTHORIZATION, auth).build();
    }

    public List<StudySummary> listStudies() {
        // QIDO-RS로 study 목록을 조회한다. UI의 검사 목록은 이 결과를 기반으로 한다.
        List<Map<String, Object>> body = dicomWeb.get()
            .uri("/studies?includefield=00081030")
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });
        if (body == null) {
            return List.of();
        }
        return body.stream()
            .map(item -> new StudySummary(
                DicomWebJson.value(item, "00100020"),
                DicomWebJson.value(item, "00080020"),
                firstNonBlank(DicomWebJson.value(item, "00080061"), DicomWebJson.value(item, "00080060")),
                DicomWebJson.value(item, "00081030"),
                DicomWebJson.value(item, "0020000D"),
                DicomWebJson.value(item, "00201206"),
                "NOT_RUN",
                "NOT_RUN"
            ))
            .toList();
    }

    public List<SeriesSummary> listSeries(String studyUid) {
        List<Map<String, Object>> body = dicomWeb.get()
            .uri("/studies/{studyUid}/series", studyUid)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });
        if (body == null) {
            return List.of();
        }
        return body.stream()
            .map(item -> new SeriesSummary(
                studyUid,
                DicomWebJson.value(item, "0020000E"),
                DicomWebJson.value(item, "00080060"),
                DicomWebJson.value(item, "0008103E"),
                DicomWebJson.value(item, "00201209")
            ))
            .toList();
    }

    public List<InstanceSummary> listInstances(String studyUid, String seriesUid) {
        List<Map<String, Object>> body = dicomWeb.get()
            .uri("/studies/{studyUid}/series/{seriesUid}/instances", studyUid, seriesUid)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });
        if (body == null) {
            return List.of();
        }
        return body.stream()
            .map(item -> new InstanceSummary(
                studyUid,
                seriesUid,
                DicomWebJson.value(item, "00080018"),
                DicomWebJson.value(item, "00080016"),
                DicomWebJson.value(item, "00200013")
            ))
            .toList();
    }

    public Map<String, Object> getMetadata(String studyUid, String seriesUid, String sopUid) {
        List<Map<String, Object>> body = dicomWeb.get()
            .uri("/studies/{studyUid}/series/{seriesUid}/instances/{sopUid}/metadata", studyUid, seriesUid, sopUid)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });
        return body == null || body.isEmpty() ? Map.of() : body.getFirst();
    }

    public byte[] getInstanceDicomBytes(String studyUid, String seriesUid, String sopUid) {
        // Orthanc 내부 ID는 DICOM UID와 다르므로, 먼저 UID 조건으로 instance를 찾고 file endpoint로 원본 bytes를 가져온다.
        List<String> ids = rest.post()
            .uri("/tools/find")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of(
                "Level", "Instance",
                "Query", Map.of(
                    "StudyInstanceUID", studyUid,
                    "SeriesInstanceUID", seriesUid,
                    "SOPInstanceUID", sopUid
                )
            ))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Orthanc에서 인스턴스를 찾을 수 없습니다: " + sopUid);
        }
        return rest.get()
            .uri("/instances/{id}/file", ids.getFirst())
            .retrieve()
            .body(byte[].class);
    }

    public String stowDicom(byte[] dicomBytes) {
        // STOW-RS는 multipart/related 형식을 요구하므로 DICOM bytes를 단일 part payload로 감싼다.
        String boundary = "hanul-stow-" + System.nanoTime();
        byte[] payload = multipartDicom(boundary, dicomBytes);
        dicomWeb.post()
            .uri("/studies")
            .contentType(MediaType.parseMediaType("multipart/related; type=\"application/dicom\"; boundary=" + boundary))
            .body(payload)
            .retrieve()
            .toBodilessEntity();
        return "STOW_RS_STORED";
    }

    public boolean verifyInstanceExists(String studyUid, String seriesUid, String sopUid) {
        // STOW 성공 후 생성된 UID로 다시 검색해 PACS에 실제 저장되었는지 확인한다.
        List<String> ids = rest.post()
            .uri("/tools/find")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of(
                "Level", "Instance",
                "Query", Map.of(
                    "StudyInstanceUID", studyUid,
                    "SeriesInstanceUID", seriesUid,
                    "SOPInstanceUID", sopUid
                )
            ))
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });
        return ids != null && !ids.isEmpty();
    }

    public boolean health() {
        rest.get().uri("/system").retrieve().toBodilessEntity();
        return true;
    }

    private static String firstNonBlank(String a, String b) {
        return a == null || a.isBlank() ? b : a;
    }

    private static byte[] multipartDicom(String boundary, byte[] dicomBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(("--" + boundary + "\r\nContent-Type: application/dicom\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.writeBytes(dicomBytes);
        out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    private static byte[] extractDicomPayload(byte[] body, MediaType contentType) {
        if (body == null) {
            return new byte[0];
        }
        boolean multipart = contentType != null && String.valueOf(contentType).toLowerCase().startsWith("multipart/");
        if (!multipart && !looksLikeMultipart(body)) {
            return body;
        }
        String boundary = cleanBoundary(contentType == null ? null : contentType.getParameter("boundary"));
        byte[] payload = extractMultipartPayload(body, boundary);
        if (payload != body) {
            return payload;
        }
        payload = extractMultipartPayload(body, boundaryFromBody(body));
        if (payload != body) {
            return payload;
        }
        return extractByDicmPreamble(body);
    }

    private static byte[] extractMultipartPayload(byte[] body, String boundary) {
        if (boundary == null || boundary.isBlank()) {
            return body;
        }
        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        List<Integer> starts = findAll(body, delimiter);
        for (int start : starts) {
            int headerStart = start + delimiter.length;
            if (headerStart + 2 < body.length && body[headerStart] == '-' && body[headerStart + 1] == '-') {
                continue;
            }
            int headerEnd = findSequence(body, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), headerStart);
            if (headerEnd < 0) {
                continue;
            }
            int payloadStart = headerEnd + 4;
            long contentLength = partContentLength(body, headerStart, headerEnd);
            int payloadEnd;
            if (contentLength >= 0 && payloadStart + contentLength <= body.length) {
                payloadEnd = (int) (payloadStart + contentLength);
            } else {
                payloadEnd = findSequence(body, ("\r\n--" + boundary).getBytes(StandardCharsets.ISO_8859_1), payloadStart);
                if (payloadEnd < 0) {
                    payloadEnd = body.length;
                }
            }
            byte[] part = new byte[payloadEnd - payloadStart];
            System.arraycopy(body, payloadStart, part, 0, part.length);
            return part;
        }
        return body;
    }

    private static long partContentLength(byte[] body, int headerStart, int headerEnd) {
        String headers = new String(body, headerStart, headerEnd - headerStart, StandardCharsets.ISO_8859_1);
        for (String line : headers.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String name = line.substring(0, colon).trim();
            if (!"Content-Length".equalsIgnoreCase(name)) {
                continue;
            }
            try {
                return Long.parseLong(line.substring(colon + 1).trim());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static String cleanBoundary(String boundary) {
        if (boundary == null) {
            return "";
        }
        return boundary.trim().replaceAll("^\"|\"$", "");
    }

    private static byte[] extractByDicmPreamble(byte[] body) {
        byte[] marker = "DICM".getBytes(StandardCharsets.ISO_8859_1);
        int markerAt = findSequence(body, marker, 0);
        if (markerAt < 128) {
            return body;
        }
        int start = markerAt - 128;
        int end = findSequence(body, "\r\n--".getBytes(StandardCharsets.ISO_8859_1), markerAt);
        if (end < 0) {
            end = body.length;
        }
        byte[] part = new byte[end - start];
        System.arraycopy(body, start, part, 0, part.length);
        return part;
    }

    private static boolean looksLikeMultipart(byte[] body) {
        return body.length > 4 && body[0] == '-' && body[1] == '-';
    }

    private static String boundaryFromBody(byte[] body) {
        if (!looksLikeMultipart(body)) {
            return "";
        }
        int lineEnd = findSequence(body, "\r\n".getBytes(StandardCharsets.ISO_8859_1), 0);
        if (lineEnd < 0) {
            return "";
        }
        return new String(body, 2, lineEnd - 2, StandardCharsets.ISO_8859_1).trim();
    }

    private static List<Integer> findAll(byte[] haystack, byte[] needle) {
        List<Integer> indexes = new ArrayList<>();
        int offset = 0;
        while (offset < haystack.length) {
            int found = findSequence(haystack, needle, offset);
            if (found < 0) {
                break;
            }
            indexes.add(found);
            offset = found + needle.length;
        }
        return indexes;
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
}
