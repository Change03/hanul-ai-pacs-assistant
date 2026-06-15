package com.hanul.aipacs.client;

import java.util.List;
import java.util.Map;

final class DicomWebJson {
    private DicomWebJson() {
    }

    @SuppressWarnings("unchecked")
    static String value(Map<String, Object> item, String tag) {
        Object node = item.get(tag);
        if (!(node instanceof Map<?, ?> map)) {
            return "";
        }
        Object value = map.get("Value");
        if (value instanceof List<?> list && !list.isEmpty()) {
            Object first = list.getFirst();
            if (first instanceof Map<?, ?> nested && nested.containsKey("Alphabetic")) {
                return String.valueOf(nested.get("Alphabetic"));
            }
            return String.valueOf(first);
        }
        return "";
    }

    static Map<String, Object> tag(Map<String, Object> item, String tag) {
        Object node = item.get(tag);
        if (node instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
