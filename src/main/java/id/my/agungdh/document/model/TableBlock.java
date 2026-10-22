package id.my.agungdh.document.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TableBlock(
        List<String> headers,
        List<List<String>> rows,
        List<Double> columnWidths,
        String headerBackground,
        String headerColor,
        String stripeColor,
        String borderColor,
        Double fontSize,
        String fontFamily,
        String align
) implements DocumentBlock {
    public TableBlock {
        headers = headers == null ? List.of() : List.copyOf(headers);
        rows = rows == null ? List.of() : rows.stream()
                .map(row -> row == null ? List.<String>of() : List.copyOf(row))
                .toList();
        columnWidths = columnWidths == null ? List.of() : List.copyOf(columnWidths);
        headerBackground = headerBackground == null ? "#1F4E79" : headerBackground;
        headerColor = headerColor == null ? "#FFFFFF" : headerColor;
        stripeColor = stripeColor == null ? "#F2F2F2" : stripeColor;
        borderColor = borderColor == null ? "#BFBFBF" : borderColor;
        align = align == null ? "left" : align.toLowerCase();
    }

    public int columnCount() {
        int count = headers.size();
        for (List<String> row : rows) {
            count = Math.max(count, row.size());
        }
        return count;
    }
}
