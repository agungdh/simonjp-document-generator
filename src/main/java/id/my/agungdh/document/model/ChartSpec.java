package id.my.agungdh.document.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Chart definition shared by every output format. Office files get a native,
 * editable chart; PDF gets a rendered PNG.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChartSpec(
        String type,
        String title,
        String xAxisTitle,
        String yAxisTitle,
        List<String> categories,
        List<ChartSeries> series,
        Boolean legend,
        List<String> palette,
        String fontFamily
) {
    public ChartSpec {
        type = type == null ? "bar" : type.toLowerCase();
        title = title == null ? "" : title;
        categories = categories == null ? List.of() : List.copyOf(categories);
        series = series == null ? List.of() : List.copyOf(series);
        legend = legend == null || legend;
        palette = palette == null ? List.of() : List.copyOf(palette);
    }
}
