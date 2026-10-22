package id.my.agungdh.document.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * One piece of content in a document. The JSON discriminator is the {@code type} property,
 * e.g. {@code {"type":"paragraph", ...}}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = HeadingBlock.class, name = "heading"),
        @JsonSubTypes.Type(value = ParagraphBlock.class, name = "paragraph"),
        @JsonSubTypes.Type(value = TableBlock.class, name = "table"),
        @JsonSubTypes.Type(value = ImageBlock.class, name = "image"),
        @JsonSubTypes.Type(value = ChartBlock.class, name = "chart"),
        @JsonSubTypes.Type(value = PageBreakBlock.class, name = "pageBreak")
})
public sealed interface DocumentBlock permits HeadingBlock, ParagraphBlock, TableBlock,
        ImageBlock, ChartBlock, PageBreakBlock {
}
