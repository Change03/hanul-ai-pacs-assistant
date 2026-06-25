package com.hanul.aipacs.ai.service;

import com.hanul.aipacs.ai.domain.ProcessedDicom;
import com.hanul.aipacs.ai.util.ImageUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;
import org.springframework.stereotype.Service;

@Service
public class DicomPreprocessor {
    public ProcessedDicom preprocess(byte[] dicomBytes, String windowPreset) {
        Attributes dataset = readDataset(dicomBytes);
        int rows = dataset.getInt(Tag.Rows, 0);
        int columns = dataset.getInt(Tag.Columns, 0);
        if (rows <= 0 || columns <= 0) {
            throw new IllegalArgumentException("DICOM rows/columns were missing");
        }
        byte[] pixelBytes = pixelBytes(dataset);
        if (pixelBytes == null || pixelBytes.length == 0) {
            throw new IllegalArgumentException("DICOM has no PixelData");
        }
        int bitsAllocated = dataset.getInt(Tag.BitsAllocated, 16);
        int bitsStored = dataset.getInt(Tag.BitsStored, bitsAllocated);
        int pixelRepresentation = dataset.getInt(Tag.PixelRepresentation, 0);
        double slope = dataset.getDouble(Tag.RescaleSlope, 1.0);
        double intercept = dataset.getDouble(Tag.RescaleIntercept, 0.0);
        String photometric = dataset.getString(Tag.PhotometricInterpretation, "MONOCHROME2");

        float[] pixels = decodePixels(pixelBytes, rows * columns, bitsAllocated, bitsStored, pixelRepresentation, slope, intercept);
        List<String> warnings = new ArrayList<>();
        if ("MONOCHROME1".equalsIgnoreCase(photometric)) {
            invert(pixels);
            warnings.add("MONOCHROME1 image inverted for display");
        }

        double[] window = windowing(dataset, pixels, windowPreset);
        byte[] image = normalize(pixels, window[0], window[1]);
        byte[] resized = ImageUtils.resizeGrayscale(image, columns, rows, 224, 224);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("rows", rows);
        metadata.put("columns", columns);
        metadata.put("windowCenter", window[0]);
        metadata.put("windowWidth", window[1]);
        metadata.put("rescaleSlope", slope);
        metadata.put("rescaleIntercept", intercept);
        return new ProcessedDicom(dataset, image, resized, rows, columns, metadata, warnings);
    }

    private Attributes readDataset(byte[] dicomBytes) {
        try (DicomInputStream input = new DicomInputStream(new ByteArrayInputStream(dicomBytes))) {
            return input.readDataset(-1, -1);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read DICOM", e);
        }
    }

    private byte[] pixelBytes(Attributes dataset) {
        try {
            return dataset.getBytes(Tag.PixelData);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read DICOM PixelData", e);
        }
    }

    private float[] decodePixels(byte[] pixelBytes, int pixelCount, int bitsAllocated, int bitsStored, int pixelRepresentation, double slope, double intercept) {
        float[] output = new float[pixelCount];
        if (bitsAllocated <= 8) {
            for (int i = 0; i < pixelCount && i < pixelBytes.length; i++) {
                output[i] = (float) (Byte.toUnsignedInt(pixelBytes[i]) * slope + intercept);
            }
            return output;
        }
        int mask = bitsStored >= 16 ? 0xFFFF : (1 << bitsStored) - 1;
        int signBit = 1 << Math.max(0, bitsStored - 1);
        for (int i = 0; i < pixelCount; i++) {
            int offset = i * 2;
            if (offset + 1 >= pixelBytes.length) {
                break;
            }
            int raw = Byte.toUnsignedInt(pixelBytes[offset]) | (Byte.toUnsignedInt(pixelBytes[offset + 1]) << 8);
            raw &= mask;
            if (pixelRepresentation == 1 && bitsStored < 16 && (raw & signBit) != 0) {
                raw -= 1 << bitsStored;
            } else if (pixelRepresentation == 1 && bitsStored >= 16) {
                raw = (short) raw;
            }
            output[i] = (float) (raw * slope + intercept);
        }
        return output;
    }

    private void invert(float[] pixels) {
        float max = Float.NEGATIVE_INFINITY;
        for (float pixel : pixels) {
            max = Math.max(max, pixel);
        }
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = max - pixels[i];
        }
    }

    private double[] windowing(Attributes dataset, float[] pixels, String preset) {
        String normalizedPreset = preset == null ? "auto" : preset.trim().toLowerCase();
        return switch (normalizedPreset) {
            case "chest" -> new double[] {40.0, 400.0};
            case "lung" -> new double[] {-600.0, 1500.0};
            case "bone" -> new double[] {300.0, 1500.0};
            default -> {
                double center = firstNumber(dataset.getStrings(Tag.WindowCenter), percentile(pixels, 50));
                double width = firstNumber(dataset.getStrings(Tag.WindowWidth), percentile(pixels, 99) - percentile(pixels, 1));
                if (width <= 1) {
                    double low = percentile(pixels, 1);
                    double high = percentile(pixels, 99);
                    center = (low + high) / 2.0;
                    width = Math.max(high - low, 1.0);
                }
                yield new double[] {center, width};
            }
        };
    }

    private byte[] normalize(float[] pixels, double center, double width) {
        double low = center - width / 2.0;
        double high = center + width / 2.0;
        double span = Math.max(high - low, 1e-6);
        byte[] output = new byte[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            double normalized = Math.max(0.0, Math.min(1.0, (pixels[i] - low) / span));
            output[i] = (byte) Math.round(normalized * 255.0);
        }
        return output;
    }

    private double firstNumber(String[] values, double fallback) {
        if (values == null || values.length == 0) {
            return fallback;
        }
        try {
            return Double.parseDouble(values[0]);
        } catch (Exception e) {
            return fallback;
        }
    }

    private double percentile(float[] values, int percentile) {
        return com.hanul.aipacs.ai.util.PercentileUtils.percentile(values, percentile);
    }
}
