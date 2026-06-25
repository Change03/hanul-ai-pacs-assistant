package com.hanul.aipacs.ai.provider;

import com.hanul.aipacs.ai.domain.BoundingBox;
import com.hanul.aipacs.ai.domain.ProcessedDicom;
import com.hanul.aipacs.ai.domain.ProviderResult;
import com.hanul.aipacs.ai.util.PercentileUtils;
import java.util.ArrayList;
import java.util.List;

public class DemoFallbackProvider implements InferenceProvider {
    @Override
    public String name() {
        return "DEMO_FALLBACK";
    }

    @Override
    public ProviderResult infer(ProcessedDicom processedDicom) {
        byte[] image = processedDicom.image();
        int rows = processedDicom.rows();
        int columns = processedDicom.columns();

        double p05 = PercentileUtils.percentile(image, 5);
        double p50 = PercentileUtils.percentile(image, 50);
        double p95 = PercentileUtils.percentile(image, 95);
        double p99 = PercentileUtils.percentile(image, 99);

        int brightPixels = 0;
        int minX = columns;
        int minY = rows;
        int maxX = -1;
        int maxY = -1;
        int brightThreshold = (int) Math.max(p95, 170);
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                int value = Byte.toUnsignedInt(image[(y * columns) + x]);
                if (value >= brightThreshold) {
                    brightPixels++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        double brightRatio = brightPixels / (double) (rows * columns);
        double contrast = (p95 - p05) / 255.0;

        String label;
        double score;
        boolean drawBox = false;
        if (brightRatio > 0.025 && p99 > 210.0) {
            label = "Opacity demo";
            score = Math.min(0.96, 0.58 + brightRatio * 7.0 + contrast * 0.25);
            drawBox = true;
        } else if (contrast < 0.18) {
            label = "Low confidence demo";
            score = 0.36;
            drawBox = brightPixels > 4;
        } else {
            label = "No acute finding demo";
            score = Math.max(0.08, Math.min(0.31, 0.2 + (p50 - 90.0) / 900.0));
        }

        List<BoundingBox> boxes = new ArrayList<>();
        if (drawBox && maxX >= minX && maxY >= minY) {
            int padX = Math.max(6, (int) (columns * 0.025));
            int padY = Math.max(6, (int) (rows * 0.025));
            int x = Math.max(0, minX - padX);
            int y = Math.max(0, minY - padY);
            int width = Math.max(1, Math.min(columns - 1, maxX + padX) - x);
            int height = Math.max(1, Math.min(rows - 1, maxY + padY) - y);
            boxes.add(new BoundingBox(x, y, width, height, label, round(score)));
        }

        return new ProviderResult(
            name(),
            label,
            round(score),
            boxes,
            List.of("No ONNX model was found; deterministic demo fallback was used.")
        );
    }

    private static double round(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }
}
