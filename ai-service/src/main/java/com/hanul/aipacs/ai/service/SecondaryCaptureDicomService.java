package com.hanul.aipacs.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanul.aipacs.ai.domain.SecondaryCaptureResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;
import org.springframework.stereotype.Service;

@Service
public class SecondaryCaptureDicomService {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecondaryCaptureResult build(Attributes source, byte[] overlayRgb, int width, int height, Map<String, Object> summary) {
        return build(source, overlayRgb, width, height, summary, UIDUtils.createUID(), UIDUtils.createUID());
    }

    public SecondaryCaptureResult build(
        Attributes source,
        byte[] overlayRgb,
        int width,
        int height,
        Map<String, Object> summary,
        String seriesInstanceUid,
        String sopInstanceUid
    ) {
        Attributes dataset = new Attributes();
        dataset.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
        dataset.setString(Tag.SOPInstanceUID, VR.UI, sopInstanceUid);
        dataset.setString(Tag.StudyInstanceUID, VR.UI, source.getString(Tag.StudyInstanceUID, UIDUtils.createUID()));
        dataset.setString(Tag.SeriesInstanceUID, VR.UI, seriesInstanceUid);
        dataset.setString(Tag.Modality, VR.CS, "OT");
        dataset.setString(Tag.SeriesDescription, VR.LO, "AI_RESULT_DEMO");
        dataset.setString(Tag.ConversionType, VR.CS, "WSD");
        dataset.setString(Tag.PatientID, VR.LO, source.getString(Tag.PatientID, "ANON_UNKNOWN"));
        dataset.setString(Tag.PatientName, VR.PN, source.getString(Tag.PatientName, "ANON^UNKNOWN"));
        dataset.setString(Tag.StudyDate, VR.DA, source.getString(Tag.StudyDate, ""));
        dataset.setString(Tag.StudyTime, VR.TM, source.getString(Tag.StudyTime, ""));
        dataset.setString(Tag.ImageType, VR.CS, "DERIVED", "SECONDARY", "DEMO");
        dataset.setString(Tag.ImageComments, VR.LT, truncate(writeJson(summary), 1024));
        dataset.setInt(Tag.Rows, VR.US, height);
        dataset.setInt(Tag.Columns, VR.US, width);
        dataset.setInt(Tag.SamplesPerPixel, VR.US, 3);
        dataset.setString(Tag.PhotometricInterpretation, VR.CS, "RGB");
        dataset.setInt(Tag.PlanarConfiguration, VR.US, 0);
        dataset.setInt(Tag.BitsAllocated, VR.US, 8);
        dataset.setInt(Tag.BitsStored, VR.US, 8);
        dataset.setInt(Tag.HighBit, VR.US, 7);
        dataset.setInt(Tag.PixelRepresentation, VR.US, 0);
        dataset.setBytes(Tag.PixelData, VR.OW, overlayRgb);

        Attributes fileMeta = dataset.createFileMetaInformation(UID.ExplicitVRLittleEndian);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); DicomOutputStream dos = new DicomOutputStream(out, UID.ExplicitVRLittleEndian)) {
            dos.writeDataset(fileMeta, dataset);
            return new SecondaryCaptureResult(out.toByteArray(), seriesInstanceUid, sopInstanceUid);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write result DICOM", e);
        }
    }

    private String writeJson(Map<String, Object> summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
