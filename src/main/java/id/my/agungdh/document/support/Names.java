package id.my.agungdh.document.support;

import java.text.Normalizer;
import java.util.Locale;

public final class Names {

    private Names() {
    }

    /** Safe, readable file name (without extension) derived from a document title. */
    public static String fileBaseName(String title) {
        String value = title == null ? "" : title;
        value = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (value.isBlank()) {
            value = "document";
        }
        if (value.length() > 80) {
            value = value.substring(0, 80).replaceAll("-+$", "");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    public static String ascii(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");
        return normalized.replaceAll("[\\r\\n\\\"]", " ").trim();
    }
}
