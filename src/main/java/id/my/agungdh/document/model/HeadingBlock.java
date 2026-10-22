package id.my.agungdh.document.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HeadingBlock(
        Integer level,
        String text,
        String align,
        String color,
        String fontFamily,
        Double fontSize,
        Double marginAfter
) implements DocumentBlock {
    public HeadingBlock {
        level = level == null ? 1 : Math.max(1, Math.min(4, level));
        text = text == null ? "" : text;
        align = align == null ? "left" : align.toLowerCase();
        marginAfter = marginAfter == null ? 10.0 : marginAfter;
    }

    public static HeadingBlock of(int level, String text) {
        return new HeadingBlock(level, text, null, null, null, null, null);
    }
}
