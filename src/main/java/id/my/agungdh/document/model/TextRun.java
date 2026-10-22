package id.my.agungdh.document.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A piece of text with its own look. Any property that is {@code null} inherits
 * from the surrounding paragraph / theme.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TextRun(
        String text,
        Boolean bold,
        Boolean italic,
        Boolean underline,
        String fontFamily,
        Double fontSize,
        String color
) {
    public TextRun {
        text = text == null ? "" : text;
        bold = Boolean.TRUE.equals(bold);
        italic = Boolean.TRUE.equals(italic);
        underline = Boolean.TRUE.equals(underline);
    }

    public static TextRun of(String text) {
        return new TextRun(text, null, null, null, null, null, null);
    }
}
