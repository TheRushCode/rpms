package com.kiranacademy.rpms.util;

import java.util.Map;

public final class RequestUtils {

    private RequestUtils() {}

    public static String getStr(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object val = map.get(key);
        return val != null ? val.toString().trim() : null;
    }

    public static String getStr(Map<String, Object> map, String key1, String key2) {
        String val = getStr(map, key1);
        return val != null ? val : getStr(map, key2);
    }

    public static String getStrWithDefault(Map<String, Object> map, String key, String defaultVal) {
        String val = getStr(map, key);
        return val != null ? val : defaultVal;
    }

    public static String getStrWithDefault(Map<String, Object> map, String key1, String key2, String defaultVal) {
        String val = getStr(map, key1, key2);
        return val != null ? val : defaultVal;
    }

    public static Double getDouble(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object val = map.get(key);
        if (val == null || val.toString().isBlank()) return null;
        try {
            return Double.parseDouble(val.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Double getDouble(Map<String, Object> map, String key1, String key2) {
        Double val = getDouble(map, key1);
        return val != null ? val : getDouble(map, key2);
    }

    public static Integer getInt(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object val = map.get(key);
        if (val == null || val.toString().isBlank()) return null;
        try {
            return Integer.parseInt(val.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer getInt(Map<String, Object> map, String key1, String key2) {
        Integer val = getInt(map, key1);
        return val != null ? val : getInt(map, key2);
    }

    public static Integer getIntWithDefault(Map<String, Object> map, String key1, String key2, Integer defaultVal) {
        Integer val = getInt(map, key1, key2);
        return val != null ? val : defaultVal;
    }
}
