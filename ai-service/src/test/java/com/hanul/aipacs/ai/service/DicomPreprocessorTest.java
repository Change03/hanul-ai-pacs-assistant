package com.hanul.aipacs.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanul.aipacs.ai.domain.ProcessedDicom;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.junit.jupiter.api.Test;

class DicomPreprocessorTest {
    @Test
    void preprocessProducesDisplayAndResizedImages() {
        DicomPreprocessor preprocessor = new DicomPreprocessor();
        ProcessedDicom processed = preprocessor.preprocess(sampleDicom(), "auto");
        assertThat(processed.rows()).isEqualTo(128);
        assertThat(processed.columns()).isEqualTo(128);
        assertThat(processed.image()).hasSize(128 * 128);
        assertThat(processed.resized()).hasSize(224 * 224);
    }

    private byte[] sampleDicom() {
        Attributes dataset = new Attributes();
        dataset.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
        dataset.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
        dataset.setString(Tag.StudyInstanceUID, VR.UI, UIDUtils.createUID());
        dataset.setString(Tag.SeriesInstanceUID, VR.UI, UIDUtils.createUID());
        dataset.setString(Tag.Modality, VR.CS, "DX");
        dataset.setString(Tag.PatientID, VR.LO, "ANON001");
        dataset.setString(Tag.PatientName, VR.PN, "ANON^DEMO001");
        dataset.setInt(Tag.Rows, VR.US, 128);
        dataset.setInt(Tag.Columns, VR.US, 128);
        dataset.setInt(Tag.SamplesPerPixel, VR.US, 1);
        dataset.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
        dataset.setInt(Tag.BitsAllocated, VR.US, 16);
        dataset.setInt(Tag.BitsStored, VR.US, 12);
        dataset.setInt(Tag.HighBit, VR.US, 11);
        dataset.setInt(Tag.PixelRepresentation, VR.US, 0);
        byte[] pixels = new byte[128 * 128 * 2];
        for (int i = 0; i < 128 * 128; i++) {
            int value = i % 4096;
            pixels[i * 2] = (byte) (value & 0xff);
            pixels[i * 2 + 1] = (byte) ((value >> 8) & 0xff);
        }
        dataset.setBytes(Tag.PixelData, VR.OW, pixels);
        Attributes fileMeta = dataset.createFileMetaInformation(UID.ExplicitVRLittleEndian);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); DicomOutputStream dos = new DicomOutputStream(out, UID.ExplicitVRLittleEndian)) {
            dos.writeDataset(fileMeta, dataset);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
