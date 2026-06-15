package com.hanul.aipacs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanul.aipacs.domain.QcStatus;
import com.hanul.aipacs.dto.QcDtos.QcReportDto;
import com.hanul.aipacs.repository.QcReportRepository;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class QcServiceTest {
    private final QcService qcService = new QcService(mock(QcReportRepository.class), new ObjectMapper());

    @Test
    void validSyntheticDicomPassesQc() {
        QcReportDto report = qcService.validateDicom(syntheticDicom(true, "ANON001", "CHEST DEMO NORMAL"));
        assertThat(report.status()).isEqualTo(QcStatus.PASS);
        assertThat(report.checks()).allSatisfy(check -> {
            if (check.severity().name().equals("ERROR")) {
                assertThat(check.passed()).isTrue();
            }
        });
    }

    @Test
    void unsignedShortPixelTagsAreRecoveredAfterSequenceTags() {
        QcReportDto report = qcService.validateDicom(syntheticDicomWithSequenceBeforePixelTags());
        assertThat(report.status()).isEqualTo(QcStatus.PASS);
        assertThat(report.checks()).allSatisfy(check -> {
            if (check.severity().name().equals("ERROR")) {
                assertThat(check.passed()).isTrue();
            }
        });
    }

    @Test
    void missingOptionalDescriptionWarns() {
        QcReportDto report = qcService.validateDicom(syntheticDicom(true, "ANON001", ""));
        assertThat(report.status()).isEqualTo(QcStatus.WARN);
    }

    @Test
    void corruptBytesFailQc() {
        QcReportDto report = qcService.validateDicom("not a dicom".getBytes(StandardCharsets.UTF_8));
        assertThat(report.status()).isEqualTo(QcStatus.FAIL);
    }

    @Test
    void nonAnonymizedPatientIdFailsQc() {
        QcReportDto report = qcService.validateDicom(syntheticDicom(true, "REAL123", "CHEST DEMO NORMAL"));
        assertThat(report.status()).isEqualTo(QcStatus.FAIL);
        assertThat(report.checks()).anyMatch(check -> check.name().equals("PatientID 익명화") && !check.passed());
    }

    static byte[] syntheticDicom(boolean includePreamble, String patientId, String studyDescription) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (includePreamble) {
            out.writeBytes(new byte[128]);
            out.writeBytes("DICM".getBytes(StandardCharsets.US_ASCII));
        }
        element(out, 0x0002, 0x0010, "UI", "1.2.840.10008.1.2.1");
        element(out, 0x0008, 0x0016, "UI", "1.2.840.10008.5.1.4.1.1.7");
        element(out, 0x0008, 0x0018, "UI", "1.2.826.0.1.3680043.10.543.1.1.1");
        element(out, 0x0008, 0x0060, "CS", "DX");
        if (!studyDescription.isBlank()) {
            element(out, 0x0008, 0x1030, "LO", studyDescription);
        }
        element(out, 0x0008, 0x103E, "LO", "CHEST PA DEMO");
        element(out, 0x0010, 0x0010, "PN", "ANON^DEMO001");
        element(out, 0x0010, 0x0020, "LO", patientId);
        element(out, 0x0020, 0x000D, "UI", "1.2.826.0.1.3680043.10.543.1");
        element(out, 0x0020, 0x000E, "UI", "1.2.826.0.1.3680043.10.543.1.1");
        element(out, 0x0028, 0x0002, "US", ushort(1));
        element(out, 0x0028, 0x0004, "CS", "MONOCHROME2");
        element(out, 0x0028, 0x0010, "US", ushort(1));
        element(out, 0x0028, 0x0011, "US", ushort(2));
        element(out, 0x0028, 0x0100, "US", ushort(16));
        element(out, 0x0028, 0x0101, "US", ushort(12));
        element(out, 0x0028, 0x0301, "CS", "NO");
        element(out, 0x7FE0, 0x0010, "OW", new byte[] {1, 2, 3, 4});
        return out.toByteArray();
    }

    static byte[] syntheticDicomWithSequenceBeforePixelTags() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[128]);
        out.writeBytes("DICM".getBytes(StandardCharsets.US_ASCII));
        element(out, 0x0002, 0x0010, "UI", "1.2.840.10008.1.2.1");
        element(out, 0x0008, 0x0016, "UI", "1.2.840.10008.5.1.4.1.1.2");
        element(out, 0x0008, 0x0018, "UI", "1.2.826.0.1.3680043.10.543.2.1.1");
        element(out, 0x0008, 0x0060, "CS", "CT");
        element(out, 0x0008, 0x1030, "LO", "BRAIN CT DEMO");
        element(out, 0x0008, 0x103E, "LO", "Brain pre 4.8 H31s");
        element(out, 0x0010, 0x0010, "PN", "ANON^BRAIN_DEMO");
        element(out, 0x0010, 0x0020, "LO", "ANON102");
        element(out, 0x0020, 0x000D, "UI", "1.2.826.0.1.3680043.10.543.2");
        element(out, 0x0020, 0x000E, "UI", "1.2.826.0.1.3680043.10.543.2.1");
        indefiniteSequence(out, 0x0008, 0x1115);
        element(out, 0x0028, 0x0002, "US", ushort(1));
        element(out, 0x0028, 0x0004, "CS", "MONOCHROME2");
        element(out, 0x0028, 0x0010, "US", ushort(1));
        element(out, 0x0028, 0x0011, "US", ushort(2));
        element(out, 0x0028, 0x0100, "US", ushort(16));
        element(out, 0x0028, 0x0101, "US", ushort(12));
        element(out, 0x0028, 0x0301, "CS", "NO");
        element(out, 0x7FE0, 0x0010, "OW", new byte[] {1, 2, 3, 4});
        return out.toByteArray();
    }

    private static void element(ByteArrayOutputStream out, int group, int element, String vr, String value) {
        element(out, group, element, vr, value.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static void element(ByteArrayOutputStream out, int group, int element, String vr, byte[] value) {
        out.write(group & 0xff);
        out.write((group >> 8) & 0xff);
        out.write(element & 0xff);
        out.write((element >> 8) & 0xff);
        out.writeBytes(vr.getBytes(StandardCharsets.US_ASCII));
        byte[] padded = value.length % 2 == 0 ? value : java.util.Arrays.copyOf(value, value.length + 1);
        if (java.util.Set.of("OB", "OW", "SQ", "UN", "UT").contains(vr)) {
            out.write(0);
            out.write(0);
            int length = padded.length;
            out.write(length & 0xff);
            out.write((length >> 8) & 0xff);
            out.write((length >> 16) & 0xff);
            out.write((length >> 24) & 0xff);
        } else {
            int length = padded.length;
            out.write(length & 0xff);
            out.write((length >> 8) & 0xff);
        }
        out.writeBytes(padded);
    }

    private static void indefiniteSequence(ByteArrayOutputStream out, int group, int element) {
        out.write(group & 0xff);
        out.write((group >> 8) & 0xff);
        out.write(element & 0xff);
        out.write((element >> 8) & 0xff);
        out.writeBytes("SQ".getBytes(StandardCharsets.US_ASCII));
        out.write(0);
        out.write(0);
        out.write(0xff);
        out.write(0xff);
        out.write(0xff);
        out.write(0xff);
    }

    private static byte[] ushort(int value) {
        return new byte[] {(byte) (value & 0xff), (byte) ((value >> 8) & 0xff)};
    }
}
