package com.smartresume.planner.common.util;

public final class Texts {

    private Texts() {
    }

    /** Null-safe truncate that never returns more than {@code max} characters. */
    public static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public static int wordCount(String s) {
        return s == null || s.isBlank() ? 0 : s.trim().split("\\s+").length;
    }
}
