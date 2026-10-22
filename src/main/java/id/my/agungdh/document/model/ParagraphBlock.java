package id.my.agungdh.document.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ParagraphBlock(
        String text,
        List<TextRun> runs,
        String align,
        String fontFamily,
        Double fontSize,
        Double lineSpacing,
        Double marginAfter,
        String color
) implements DocumentBlock {
    public ParagraphBlock {
        runs = runs == null ? List.of() : List.copyOf(runs);
        if (runs.isEmpty() && text != null && !text.isEmpty()) {
            runs = List.of(TextRun.of(text));
        }
        align = align == null ? "left" : align.toLowerCase();
        lineSpacing = lineSpacing == null ? 1.15 : lineSpacing;
        marginAfter = marginAfter == null ? 8.0 : marginAfter;
    }

    public static ParagraphBlock of(String text) {
        return new ParagraphBlock(text, null, null, null, null, null, null, null);
    }
}
