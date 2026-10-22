package id.my.agungdh.document.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * An image from a data URI, a public http(s) URL, or a raw base64 string.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ImageBlock(
        String src,
        Double width,
        Double height,
        String align,
        String caption,
        Boolean border
) implements DocumentBlock {
    public ImageBlock {
        align = align == null ? "center" : align.toLowerCase();
        border = Boolean.TRUE.equals(border);
    }
}
