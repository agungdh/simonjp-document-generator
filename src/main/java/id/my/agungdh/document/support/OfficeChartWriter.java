package id.my.agungdh.document.support;

import id.my.agungdh.document.model.ChartSeries;
import id.my.agungdh.document.model.ChartSpec;
import org.apache.poi.xddf.usermodel.XDDFColor;
import org.apache.poi.xddf.usermodel.XDDFLineProperties;
import org.apache.poi.xddf.usermodel.XDDFSolidFillProperties;
import org.apache.poi.xddf.usermodel.chart.AxisCrosses;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.BarGrouping;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.Grouping;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChart;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFLineChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;

import java.awt.Color;
import java.util.List;

/**
 * Populates a native Office chart (Word / Excel / PowerPoint) with the data and
 * styling from a {@link ChartSpec}. The chart stays editable in Office.
 */
public final class OfficeChartWriter {

    public void populate(XDDFChart chart, ChartSpec spec) {
        String kind = ChartKinds.normalize(spec.type());
        if (!ChartKinds.SUPPORTED.contains(kind)) {
            throw new IllegalArgumentException("Unsupported chart type: " + spec.type());
        }

        if (spec.title() != null && !spec.title().isBlank()) {
            chart.setTitleText(spec.title());
        } else {
            chart.setAutoTitleDeleted(true);
        }

        boolean pie = ChartKinds.isPie(kind);
        XDDFCategoryAxis categoryAxis = null;
        XDDFValueAxis valueAxis = null;
        if (!pie) {
            categoryAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
            valueAxis = chart.createValueAxis(AxisPosition.LEFT);
            valueAxis.setCrosses(AxisCrosses.AUTO_ZERO);
            if (spec.xAxisTitle() != null && !spec.xAxisTitle().isBlank()) {
                categoryAxis.setTitle(spec.xAxisTitle());
            }
            if (spec.yAxisTitle() != null && !spec.yAxisTitle().isBlank()) {
                valueAxis.setTitle(spec.yAxisTitle());
            }
        }

        XDDFChartData data = switch (kind) {
            case "bar" -> chart.createData(ChartTypes.BAR, categoryAxis, valueAxis);
            case "horizontalbar" -> chart.createData(ChartTypes.BAR, categoryAxis, valueAxis);
            case "stackedbar" -> chart.createData(ChartTypes.BAR, categoryAxis, valueAxis);
            case "line" -> chart.createData(ChartTypes.LINE, categoryAxis, valueAxis);
            case "area" -> chart.createData(ChartTypes.AREA, categoryAxis, valueAxis);
            case "stackedarea" -> chart.createData(ChartTypes.AREA, categoryAxis, valueAxis);
            case "pie" -> chart.createData(ChartTypes.PIE, null, null);
            case "doughnut" -> chart.createData(ChartTypes.DOUGHNUT, null, null);
            default -> throw new IllegalArgumentException("Unsupported chart type: " + spec.type());
        };

        if (data instanceof XDDFBarChartData bar) {
            bar.setBarDirection(kind.equals("horizontalbar") ? BarDirection.BAR : BarDirection.COL);
            bar.setBarGrouping(kind.equals("stackedbar") ? BarGrouping.STACKED : BarGrouping.CLUSTERED);
        } else if (data instanceof XDDFLineChartData line) {
            line.setGrouping(Grouping.STANDARD);
        } else if (data instanceof org.apache.poi.xddf.usermodel.chart.XDDFAreaChartData area) {
            area.setGrouping(kind.equals("stackedarea") ? Grouping.STACKED : Grouping.STANDARD);
        }

        List<Color> colors = palette(spec);
        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromArray(
                spec.categories().toArray(String[]::new));

        List<ChartSeries> seriesList = pie ? List.of(spec.series().getFirst()) : spec.series();
        int seriesIndex = 0;
        for (ChartSeries series : seriesList) {
            Double[] values = series.values().stream()
                    .limit(spec.categories().size())
                    .map(v -> v == null || !Double.isFinite(v) ? 0.0 : v)
                    .toArray(Double[]::new);
            if (values.length < spec.categories().size()) {
                Double[] padded = new Double[spec.categories().size()];
                System.arraycopy(values, 0, padded, 0, values.length);
                for (int i = values.length; i < padded.length; i++) {
                    padded[i] = 0.0;
                }
                values = padded;
            }
            XDDFNumericalDataSource<Double> numbers = XDDFDataSourcesFactory.fromArray(values);
            XDDFChartData.Series created = data.addSeries(categories, numbers);
            created.setTitle(series.name());

            Color color = colors.get(seriesIndex % colors.size());
            if (kind.equals("line")) {
                XDDFLineProperties line = new XDDFLineProperties(solid(color));
                line.setWidth(2.5);
                created.setLineProperties(line);
            } else if (pie) {
                for (int i = 0; i < values.length; i++) {
                    created.getDataPoint(i).setFillProperties(solid(colors.get(i % colors.size())));
                }
            } else {
                created.setFillProperties(solid(color));
            }
            seriesIndex++;
        }

        if (pie) {
            data.setVaryColors(true);
        }
        chart.plot(data);

        if (spec.legend()) {
            chart.getOrAddLegend().setPosition(LegendPosition.RIGHT);
        } else {
            chart.deleteLegend();
        }
    }

    private List<Color> palette(ChartSpec spec) {
        List<String> requested = spec.palette().isEmpty() ? Styles.PALETTE : spec.palette();
        List<Color> colors = requested.stream()
                .map(Colors::toHexOrNull)
                .filter(java.util.Objects::nonNull)
                .map(Colors::parse)
                .toList();
        return colors.isEmpty() ? Styles.PALETTE.stream().map(Colors::parse).toList() : colors;
    }

    private XDDFSolidFillProperties solid(Color color) {
        return new XDDFSolidFillProperties(XDDFColor.from(new byte[]{
                (byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue()
        }));
    }
}
