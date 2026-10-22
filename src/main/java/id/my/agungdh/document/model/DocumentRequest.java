package id.my.agungdh.document.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentRequest(
        String title,
        String subtitle,
        String author,
        String subject,
        String pageSize,
        String orientation,
        List<DocumentBlock> blocks
) {
    public DocumentRequest {
        title = title == null || title.isBlank() ? "Untitled Document" : title;
        pageSize = pageSize == null ? "A4" : pageSize.toUpperCase();
        orientation = orientation == null ? "portrait" : orientation.toLowerCase();
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }
}
