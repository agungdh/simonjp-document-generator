package id.my.agungdh.document.support;

import java.util.Set;

public final class ChartKinds {

    public static final Set<String> SUPPORTED = Set.of(
            "bar", "column", "horizontalbar", "stackedbar",
            "line", "area", "stackedarea",
            "pie", "doughnut"
    );

    private ChartKinds() {
    }

    public static String normalize(String type) {
        if (type == null) {
            return "bar";
        }
        String value = type.trim().toLowerCase().replace("-", "").replace("_", "");
        return switch (value) {
            case "column", "verticalbar" -> "bar";
            case "horizontalbar", "hbar" -> "horizontalbar";
            case "stackedbar", "stackedcolumn" -> "stackedbar";
            case "stackedarea" -> "stackedarea";
            case "ring", "donut" -> "doughnut";
            default -> value;
        };
    }

    public static boolean isPie(String type) {
        String normalized = normalize(type);
        return normalized.equals("pie") || normalized.equals("doughnut");
    }
}
