package com.hanul.aipacs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanul.aipacs.domain.QcReportEntity;
import com.hanul.aipacs.domain.enums.QcStatus;
import com.hanul.aipacs.domain.enums.Severity;
import com.hanul.aipacs.dto.QcDtos.QcCheckDto;
import com.hanul.aipacs.dto.QcDtos.QcReportDto;
import com.hanul.aipacs.repository.QcReportRepository;
import com.hanul.aipacs.util.UidUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QcService {
    // 데모 seed 데이터는 ANON### 형태를 약속한다. 이 패턴을 벗어난 PatientID는 PHI 위험으로 본다.
    private static final Pattern ANON_PATIENT_ID = Pattern.compile("^ANON\\d{3,}$");
    private static final Pattern PHI_HINT = Pattern.compile("(?i).*(REAL|MRN|DOB|HOSPITAL|KIM|LEE|PARK|JOHN|JANE|SMITH|PATIENT NAME).*");
    private static final String EXPLICIT_VR_LITTLE_ENDIAN = "1.2.840.10008.1.2.1";

    private final QcReportRepository reports;
    private final ObjectMapper objectMapper;

    public QcService(QcReportRepository reports, ObjectMapper objectMapper) {
        this.reports = reports;
        this.objectMapper = objectMapper;
    }

    public QcReportDto validateDicom(byte[] bytes) {
        List<QcCheckDto> checks = new ArrayList<>();
        DicomLiteParser.ParsedDicom parsed = DicomLiteParser.parse(bytes);

        // DICOM 구조 자체가 깨졌거나 파일 메타가 없으면 뒤 단계의 판독/추론을 신뢰할 수 없다.
        check(checks, "DICOM_STRUCTURE", "DICOM 파싱 가능", Severity.ERROR, !parsed.values().isEmpty(), "DICOM 태그를 파싱했습니다", parsed.values().isEmpty() ? "파싱된 태그 없음" : parsed.values().size() + "개 태그", "Explicit VR Little Endian DICOM 태그", "손상된 파일은 거부하거나 이 데모용 Explicit VR Little Endian으로 변환하세요.");
        check(checks, "DICOM_STRUCTURE", "DICM 프리앰블", Severity.ERROR, parsed.hasPreamble(), "DICOM Part 10 프리앰블을 확인했습니다", parsed.hasPreamble() ? "DICM 있음" : "DICM 없음", "128바이트 프리앰블과 DICM 마커", "DICOM Part 10 파일로 내보내세요.");

        // UID는 PACS 재조회, STOW read-back, 감사 로그의 기준점이므로 blocking ERROR로 다룬다.
        requireUid(checks, parsed, "StudyInstanceUID", "0020000D");
        requireUid(checks, parsed, "SeriesInstanceUID", "0020000E");
        requireUid(checks, parsed, "SOPInstanceUID", "00080018");
        requireUid(checks, parsed, "SOPClassUID", "00080016");

        String modality = parsed.value("00080060");
        check(checks, "DICOM_STRUCTURE", "Modality 존재", Severity.ERROR, present(modality), "Modality 태그가 있습니다", blankToMissing(modality), "DX, CR, CT 같은 합성 모달리티", "DICOM 태그 (0008,0060)를 채우세요.");

        String patientId = parsed.value("00100020");
        String patientName = parsed.value("00100010");
        check(checks, "PRIVACY", "PatientID 존재", Severity.ERROR, present(patientId), "PatientID 태그가 있습니다", blankToMissing(patientId), "ANON### 형식", "익명화된 PatientID를 채우세요.");
        check(checks, "PRIVACY", "PatientID 익명화", Severity.ERROR, present(patientId) && ANON_PATIENT_ID.matcher(patientId).matches(), "PatientID가 ANON### 데모 패턴을 따릅니다", blankToMissing(patientId), "ANON001 같은 합성 ID", "식별자를 ANON001 같은 합성 ID로 바꾸세요.");
        check(checks, "PRIVACY", "PatientName 익명화", Severity.ERROR, !present(patientName) || isAllowedSynthetic(patientName), "PatientName이 비어 있거나 합성 데이터처럼 보입니다", blankToMissing(patientName), "ANON^DEMO### 또는 빈 값", "실명 대신 합성 이름을 사용하세요.");

        // AI 서비스는 PixelData와 기본 영상 태그가 있어야 렌더링/전처리를 할 수 있다.
        check(checks, "PIXEL_INTEGRITY", "PixelData 존재", Severity.ERROR, parsed.hasPixelData(), "PixelData 태그가 있습니다", parsed.hasPixelData() ? "PixelData 있음" : "PixelData 없음", "AI 입력용 PixelData", "PixelData가 있는 이미지 저장 인스턴스를 사용하세요.");
        check(checks, "PIXEL_INTEGRITY", "Rows 존재", Severity.ERROR, positiveInt(parsed.value("00280010")), "Rows 태그가 있습니다", blankToMissing(parsed.value("00280010")), "양의 정수 Rows", "DICOM 태그 (0028,0010)를 채우세요.");
        check(checks, "PIXEL_INTEGRITY", "Columns 존재", Severity.ERROR, positiveInt(parsed.value("00280011")), "Columns 태그가 있습니다", blankToMissing(parsed.value("00280011")), "양의 정수 Columns", "DICOM 태그 (0028,0011)를 채우세요.");
        check(checks, "PIXEL_INTEGRITY", "BitsAllocated/BitsStored 관계", Severity.ERROR, bitsSane(parsed.value("00280100"), parsed.value("00280101")), "BitsAllocated와 BitsStored 관계가 유효합니다", "BitsAllocated=" + blankToMissing(parsed.value("00280100")) + ", BitsStored=" + blankToMissing(parsed.value("00280101")), "BitsAllocated >= BitsStored > 0", "픽셀 비트 깊이 태그를 올바르게 채우세요.");
        check(checks, "PIXEL_INTEGRITY", "PhotometricInterpretation 존재", Severity.ERROR, present(parsed.value("00280004")), "PhotometricInterpretation 태그가 있습니다", blankToMissing(parsed.value("00280004")), "MONOCHROME2 또는 RGB", "DICOM 태그 (0028,0004)를 채우세요.");
        check(checks, "PIXEL_INTEGRITY", "PixelData 길이", Severity.ERROR, pixelLengthSane(parsed), "PixelData 길이가 행/열/비트 정보와 대략 일치합니다", observedPixelLength(parsed), "Rows x Columns x BitsAllocated 기반 최소 길이", "PixelData가 잘렸거나 태그 값이 잘못된 경우 다시 내보내세요.");

        String transferSyntax = parsed.value("00020010");
        // 데모 parser와 브라우저 preview는 uncompressed Explicit VR Little Endian을 기준으로 설계되어 있다.
        check(checks, "TRANSFER_SYNTAX", "TransferSyntaxUID 존재", Severity.ERROR, present(transferSyntax), "파일 메타에서 TransferSyntaxUID를 찾았습니다", blankToMissing(transferSyntax), EXPLICIT_VR_LITTLE_ENDIAN, "TransferSyntaxUID가 포함된 파일 메타 정보를 넣으세요.");
        check(checks, "TRANSFER_SYNTAX", "TransferSyntaxUID 지원", Severity.ERROR, EXPLICIT_VR_LITTLE_ENDIAN.equals(transferSyntax), "지원하는 Explicit VR Little Endian입니다", blankToMissing(transferSyntax), EXPLICIT_VR_LITTLE_ENDIAN, "압축 전송 구문은 이 데모에서 지원하지 않습니다. Explicit VR Little Endian으로 변환하세요.");

        // 아래 항목들은 대체로 심사/운영 가독성을 높이는 경고성 검사다.
        check(checks, "AI_READINESS", "단일 프레임 데모 입력", Severity.INFO, true, "이 데모는 단일 프레임 grayscale DICOM을 대상으로 합니다", "SamplesPerPixel=" + blankToMissing(parsed.value("00280002")), "single-frame grayscale", "");
        check(checks, "DICOM_STRUCTURE", "StudyDescription 권장", Severity.WARN, present(parsed.value("00081030")), "StudyDescription이 데모 검토에 도움이 됩니다", blankToMissing(parsed.value("00081030")), "CHEST DEMO NORMAL 같은 합성 비식별 설명", "CHEST DEMO NORMAL 같은 합성 비식별 StudyDescription을 추가하세요.");
        check(checks, "DICOM_STRUCTURE", "SeriesDescription 권장", Severity.WARN, present(parsed.value("0008103E")), "SeriesDescription이 데모 검토에 도움이 됩니다", blankToMissing(parsed.value("0008103E")), "합성 비식별 SeriesDescription", "합성 비식별 SeriesDescription을 추가하세요.");
        checkPhi(checks, "PatientName", patientName, true);
        checkPhi(checks, "PatientID", patientId, true);
        checkPhi(checks, "StudyDescription", parsed.value("00081030"), false);
        checkPhi(checks, "SeriesDescription", parsed.value("0008103E"), false);
        checkPhi(checks, "InstitutionName", parsed.value("00080080"), false);
        checkPhi(checks, "ReferringPhysicianName", parsed.value("00080090"), false);

        check(checks, "DICOM_STRUCTURE", "Private tag 요약", Severity.WARN, parsed.privateTagCount() == 0, "Private tag가 없습니다", parsed.privateTagCount() + "개", "0개", "데모 입력에서는 private tag를 제거하는 것을 권장합니다.");
        String burnedIn = parsed.value("00280301");
        boolean burnedInOk = present(burnedIn) && "NO".equalsIgnoreCase(burnedIn);
        check(checks, "PRIVACY", "BurnedInAnnotation 확인", Severity.WARN, burnedInOk, "BurnedInAnnotation=NO로 표시되어 있습니다", blankToMissing(burnedIn), "NO", "영상 안에 식별 정보가 박혀 있지 않음을 확인하고 BurnedInAnnotation=NO를 설정하세요.");

        // 최종 상태는 실패한 ERROR가 있으면 FAIL, 실패한 WARN만 있으면 WARN, 모두 통과하면 PASS다.
        boolean hasError = checks.stream().anyMatch(c -> c.severity() == Severity.ERROR && !c.passed());
        boolean hasWarn = checks.stream().anyMatch(c -> c.severity() == Severity.WARN && !c.passed());
        QcStatus status = hasError ? QcStatus.FAIL : hasWarn ? QcStatus.WARN : QcStatus.PASS;
        return new QcReportDto(null, status, checks, null);
    }

    @Transactional
    public QcReportDto validateAndSave(byte[] bytes, String studyUid, String seriesUid, String sopUid) {
        QcReportDto report = validateDicom(bytes);
        QcReportEntity entity = new QcReportEntity();
        entity.setStudyInstanceUid(studyUid);
        entity.setSeriesInstanceUid(seriesUid);
        entity.setSopInstanceUid(sopUid);
        entity.setStatus(report.status().name());
        entity.setReportJson(writeJson(report));
        QcReportEntity saved = reports.save(entity);
        return new QcReportDto(saved.getId(), report.status(), report.checks(), saved.getCreatedAt());
    }

    private void requireUid(List<QcCheckDto> checks, DicomLiteParser.ParsedDicom parsed, String name, String tag) {
        String uid = parsed.value(tag);
        check(checks, "UID_INTEGRITY", name + " 존재", Severity.ERROR, present(uid), name + " 태그가 있습니다", blankToMissing(uid), name + " 값", name + " 값을 채우세요.");
        check(checks, "UID_INTEGRITY", name + " 형식", Severity.ERROR, UidUtil.isValidDicomUid(uid), name + " 값이 유효한 DICOM UID 형식입니다", blankToMissing(uid), "숫자와 점으로 구성된 64자 이하 UID", "숫자와 점으로 구성된 64자 이하 UID를 사용하세요.");
    }

    private void checkPhi(List<QcCheckDto> checks, String field, String value, boolean blocking) {
        if (!present(value)) {
            check(checks, "PRIVACY", field + " PHI 없음", Severity.INFO, true, field + " 값이 비어 있거나 없습니다", "비어 있음", "합성/비식별 값", "");
            return;
        }
        boolean allowedSynthetic = isAllowedSynthetic(value);
        boolean suspicious = PHI_HINT.matcher(value).matches();
        Severity severity = blocking ? Severity.ERROR : Severity.WARN;
        check(checks, "PRIVACY", field + " PHI 없음", severity, allowedSynthetic || !suspicious, field + " 값이 합성 데이터처럼 보입니다", value, "합성/비식별 값", "이름, MRN, 기관명, 생년월일, 병원 식별자를 제거하세요.");
    }

    private static void check(List<QcCheckDto> checks, String category, String name, Severity severity, boolean passed, String okMessage, String observed, String expectedHint, String fix) {
        checks.add(new QcCheckDto(category, name, severity, passed, passed ? okMessage : "검사 실패: " + name, observed, expectedHint, fix));
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isAllowedSynthetic(String value) {
        return value != null && (value.startsWith("ANON") || value.contains("DEMO") || value.contains("SYNTHETIC") || value.contains("HANUL"));
    }

    private static String blankToMissing(String value) {
        return present(value) ? value : "없음";
    }

    private static boolean positiveInt(String value) {
        try {
            return Integer.parseInt(value) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean bitsSane(String allocated, String stored) {
        try {
            int bitsAllocated = Integer.parseInt(allocated);
            int bitsStored = Integer.parseInt(stored);
            return bitsAllocated > 0 && bitsStored > 0 && bitsAllocated >= bitsStored;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean pixelLengthSane(DicomLiteParser.ParsedDicom parsed) {
        if (!parsed.hasPixelData() || parsed.pixelDataLength() < 0) {
            return parsed.hasPixelData();
        }
        try {
            long rows = Long.parseLong(parsed.value("00280010"));
            long columns = Long.parseLong(parsed.value("00280011"));
            long bitsAllocated = Long.parseLong(parsed.value("00280100"));
            long expectedMin = rows * columns * Math.max(1, bitsAllocated / 8);
            return parsed.pixelDataLength() >= expectedMin;
        } catch (Exception e) {
            return true;
        }
    }

    private static String observedPixelLength(DicomLiteParser.ParsedDicom parsed) {
        return "PixelDataLength=" + parsed.pixelDataLength()
            + ", Rows=" + blankToMissing(parsed.value("00280010"))
            + ", Columns=" + blankToMissing(parsed.value("00280011"))
            + ", BitsAllocated=" + blankToMissing(parsed.value("00280100"));
    }

    private String writeJson(QcReportDto report) {
        try {
            return objectMapper.writeValueAsString(Map.of("status", report.status(), "checks", report.checks()));
        } catch (Exception e) {
            return "{\"status\":\"" + report.status() + "\"}";
        }
    }
}
