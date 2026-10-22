package id.my.agungdh.document.support;

import java.awt.Color;
import java.util.Locale;
import java.util.Map;

public final class Colors {

    public static final String BLACK = "#000000";
    public static final String BODY = "#222222";
    public static final String MUTED = "#666666";
    public static final String PRIMARY = "#1F4E79";
    public static final String BORDER = "#BFBFBF";
    public static final String CAPTION = "#666666";

    private static final Map<String, String> NAMED = Map.ofEntries(
            Map.entry("black", "#000000"),
            Map.entry("white", "#FFFFFF"),
            Map.entry("red", "#FF0000"),
            Map.entry("green", "#008000"),
            Map.entry("blue", "#0000FF"),
            Map.entry("yellow", "#FFFF00"),
            Map.entry("orange", "#FFA500"),
            Map.entry("purple", "#800080"),
            Map.entry("gray", "#808080"),
            Map.entry("grey", "#808080"),
            Map.entry("navy", "#000080"),
            Map.entry("teal", "#008080"),
            Map.entry("maroon", "#800000"),
            Map.entry("silver", "#C0C0C0")
    );

    private Colors() {
    }

    /**
     * Normalizes a CSS-ish color to {@code #RRGGBB}. Returns {@code fallback} for
     * blank/invalid input.
     */
    public static String normalize(String value, String fallback) {
        String hex = toHexOrNull(value);
        return hex == null ? fallback : hex;
    }

    public static String toHexOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        String named = NAMED.get(v.toLowerCase(Locale.ROOT));
        if (named != null) {
            return named;
        }
        if (!v.startsWith("#")) {
            v = "#" + v;
        }
        if (v.matches("^#[0-9a-fA-F]{3}$")) {
            char r = v.charAt(1);
            char g = v.charAt(2);
            char b = v.charAt(3);
            return ("#" + r + r + g + g + b + b).toUpperCase(Locale.ROOT);
        }
        if (v.matches("^#[0-9a-fA-F]{6}$")) {
            return v.toUpperCase(Locale.ROOT);
        }
        return null;
    }

    public static Color parse(String value, Color fallback) {
        String hex = toHexOrNull(value);
        if (hex == null) {
            return fallback;
        }
        return new Color(
                Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16),
                Integer.parseInt(hex.substring(5, 7), 16)
        );
    }

    public static Color parse(String value) {
        return parse(value, new Color(0x22, 0x22, 0x22));
    }

    public static String toHex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    /** POI wants hex without the leading '#'. */
    public static String forPoi(String value, String fallback) {
        return normalize(value, fallback).substring(1);
    }
}
