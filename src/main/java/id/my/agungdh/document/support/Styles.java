package id.my.agungdh.document.support;

import java.util.List;

public final class Styles {

    public static final String DEFAULT_FONT = "Arial";
    public static final String DEFAULT_MONO_FONT = "Courier New";
    public static final String DEFAULT_SERIF_FONT = "Times New Roman";
    public static final double BODY_SIZE = 11.0;
    public static final double TABLE_SIZE = 9.5;
    public static final double CAPTION_SIZE = 9.0;
    public static final String CAPTION_COLOR = "#666666";

    /** A friendly, colour-blind-reasonable default chart palette. */
    public static final List<String> PALETTE = List.of(
            "#2E75B6", "#ED7D31", "#70AD47", "#A5A5A5",
            "#FFC000", "#5B9BD5", "#264478", "#9E480E",
            "#636363", "#997300"
    );

    private Styles() {
    }

    public static String font(String family) {
        return family == null || family.isBlank() ? DEFAULT_FONT : family;
    }

    public static double size(Double size, double fallback) {
        if (size == null || size <= 0) {
            return fallback;
        }
        return Math.max(4.0, Math.min(200.0, size));
    }

    /** Maps a requested family to one of the three fonts bundled with the PDF renderer. */
    public static String pdfFont(String family) {
        String f = family == null ? "" : family.toLowerCase();
        if (f.contains("courier") || f.contains("mono") || f.contains("consol") || f.contains("code")) {
            return "Liberation Mono";
        }
        if (f.contains("times") || f.contains("serif") || f.contains("georgia")
                || f.contains("cambria") || f.contains("garamond") || f.contains("book")
                || f.contains("roman")) {
            return "Liberation Serif";
        }
        return "Liberation Sans";
    }

    /** Java2D logical font used while rasterizing charts. */
    public static String awtFont(String family) {
        String f = family == null ? "" : family.toLowerCase();
        if (f.contains("courier") || f.contains("mono") || f.contains("consol") || f.contains("code")) {
            return java.awt.Font.MONOSPACED;
        }
        if (f.contains("times") || f.contains("serif") || f.contains("georgia")
                || f.contains("cambria") || f.contains("garamond") || f.contains("book")
                || f.contains("roman")) {
            return java.awt.Font.SERIF;
        }
        return java.awt.Font.SANS_SERIF;
    }

    public static String headingFont(int level) {
        return DEFAULT_FONT;
    }

    public static double headingSize(int level) {
        return switch (level) {
            case 1 -> 24.0;
            case 2 -> 18.0;
            case 3 -> 14.0;
            default -> 12.0;
        };
    }
}
