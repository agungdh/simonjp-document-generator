package id.my.agungdh.document.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChartBlock(
        ChartSpec chart,
        Double width,
        Double height,
        String align,
        String caption
) implements DocumentBlock {
    public ChartBlock {
        align = align == null ? "center" : align.toLowerCase();
    }
}
