package id.my.agungdh.document.support;

public enum Align {
    LEFT, CENTER, RIGHT, JUSTIFY;

    public static Align from(String value) {
        if (value == null) {
            return LEFT;
        }
        return switch (value.trim().toLowerCase()) {
            case "center", "centre" -> CENTER;
            case "right", "end" -> RIGHT;
            case "justify", "both" -> JUSTIFY;
            default -> LEFT;
        };
    }
}
