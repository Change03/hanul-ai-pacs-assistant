package com.hanul.aipacs.ai.service;

import org.springframework.stereotype.Service;

@Service
public class HeatmapService {
    public byte[] makeHeatmap(byte[] image, int width, int height) {
        double threshold = com.hanul.aipacs.ai.util.PercentileUtils.percentile(image, 93);
        long sumX = 0;
        long sumY = 0;
        long count = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = Byte.toUnsignedInt(image[(y * width) + x]);
                if (value >= threshold) {
                    sumX += x;
                    sumY += y;
                    count++;
                }
            }
        }
        int centerX = count == 0 ? width / 2 : (int) (sumX / count);
        int centerY = count == 0 ? height / 2 : (int) (sumY / count);
        double sigma = Math.max(Math.min(width, height) / 5.0, 1.0);
        byte[] rgb = new byte[width * height * 3];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double distance = ((x - centerX) * (x - centerX)) + ((y - centerY) * (y - centerY));
                int blob = (int) Math.round(Math.exp(-(distance / (2 * sigma * sigma))) * 255.0);
                int offset = (y * width + x) * 3;
                rgb[offset] = (byte) blob;
                rgb[offset + 1] = (byte) Math.min(255, Math.round(blob * 0.45));
                rgb[offset + 2] = (byte) Math.max(0, 255 - blob);
            }
        }
        return rgb;
    }
}
