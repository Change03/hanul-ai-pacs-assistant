package com.hanul.aipacs.util;

public final class UidUtil {
    private UidUtil() {
    }

    public static boolean isValidDicomUid(String value) {
        if (value == null || value.isBlank() || value.length() > 64) {
            return false;
        }
        if (!value.matches("[0-9]+(\\.[0-9]+)*")) {
            return false;
        }
        String[] parts = value.split("\\.");
        for (String part : parts) {
            if (part.length() > 1 && part.startsWith("0")) {
                return false;
            }
        }
        return true;
    }
}
