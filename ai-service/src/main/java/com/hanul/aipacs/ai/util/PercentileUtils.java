package com.hanul.aipacs.ai.util;

import java.util.Arrays;

public final class PercentileUtils {
    private PercentileUtils() {
    }

    public static double percentile(byte[] values, int percentile) {
        int[] ints = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            ints[i] = Byte.toUnsignedInt(values[i]);
        }
        Arrays.sort(ints);
        int index = Math.min(ints.length - 1, Math.max(0, (ints.length * percentile) / 100));
        return ints[index];
    }

    public static double percentile(float[] values, int percentile) {
        float[] copy = values.clone();
        Arrays.sort(copy);
        int index = Math.min(copy.length - 1, Math.max(0, (copy.length * percentile) / 100));
        return copy[index];
    }
}
