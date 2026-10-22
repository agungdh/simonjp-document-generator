package id.my.agungdh.document.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChartSeries(String name, java.util.List<Double> values) {
    public ChartSeries {
        name = name == null ? "" : name;
        values = values == null ? java.util.List.of() : java.util.List.copyOf(values);
    }
}
