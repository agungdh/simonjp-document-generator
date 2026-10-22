package id.my.agungdh.document.support;

import id.my.agungdh.document.model.ChartSeries;
import id.my.agungdh.document.model.ChartSpec;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.RingPlot;
import org.jfree.chart.renderer.category.AreaRenderer;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.chart.title.TextTitle;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Rasterizes a {@link ChartSpec} to PNG using JFreeChart. Used by the PDF
 * renderer; Office formats use native, editable charts instead.
 */
public final class ChartImageRenderer {

    public byte[] render(ChartSpec spec, int width, int height) {
        if (spec == null || spec.categories().isEmpty() || spec.series().isEmpty()) {
            throw new IllegalArgumentException("A chart needs at least one category and one data series");
        }
        String kind = ChartKinds.normalize(spec.type());
        if (!ChartKinds.SUPPORTED.contains(kind)) {
            throw new IllegalArgumentException("Unsupported chart type: " + spec.type());
        }
        String family = Styles.awtFont(spec.fontFamily());
        JFreeChart chart = ChartKinds.isPie(kind)
                ? createPie(spec, kind, family)
                : createCategory(spec, kind, family);

        if (spec.title() != null && !spec.title().isBlank()) {
            chart.setTitle(spec.title());
            chart.getTitle().setFont(new Font(family, Font.BOLD, 18));
            chart.getTitle().setPaint(new Color(0x33, 0x33, 0x33));
        }
        if (chart.getLegend() != null) {
            chart.getLegend().setItemFont(new Font(family, Font.PLAIN, 11));
            chart.getLegend().setBackgroundPaint(Color.WHITE);
            chart.getLegend().setBorder(0, 0, 0, 0);
            chart.getLegend().setItemPaint(new Color(0x44, 0x44, 0x44));
        }
        chart.setBackgroundPaint(Color.WHITE);
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);
        chart.setPadding(new RectangleInsets(8, 8, 8, 8));

        BufferedImage image = chart.createBufferedImage(Math.max(64, width), Math.max(64, height));
        return Images.toPng(image);
    }

    private JFreeChart createCategory(ChartSpec spec, String kind, String family) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (ChartSeries series : spec.series()) {
            List<Double> values = series.values();
            for (int i = 0; i < spec.categories().size() && i < values.size(); i++) {
                Double value = values.get(i);
                if (value != null && Double.isFinite(value)) {
                    dataset.addValue(value, series.name(), spec.categories().get(i));
                }
            }
        }
        if (dataset.getRowCount() == 0) {
            throw new IllegalArgumentException("The chart has no usable data values");
        }

        String title = spec.title() == null || spec.title().isBlank() ? null : spec.title();
        String x = blankToNull(spec.xAxisTitle());
        String y = blankToNull(spec.yAxisTitle());
        boolean legend = spec.legend();
        JFreeChart chart = switch (kind) {
            case "horizontalbar" -> ChartFactory.createBarChart(
                    title, x, y, dataset, PlotOrientation.HORIZONTAL, legend, false, false);
            case "stackedbar" -> ChartFactory.createStackedBarChart(
                    title, x, y, dataset, PlotOrientation.VERTICAL, legend, false, false);
            case "line" -> ChartFactory.createLineChart(
                    title, x, y, dataset, PlotOrientation.VERTICAL, legend, false, false);
            case "area" -> ChartFactory.createAreaChart(
                    title, x, y, dataset, PlotOrientation.VERTICAL, legend, false, false);
            case "stackedarea" -> ChartFactory.createStackedAreaChart(
                    title, x, y, dataset, PlotOrientation.VERTICAL, legend, false, false);
            default -> ChartFactory.createBarChart(
                    title, x, y, dataset, PlotOrientation.VERTICAL, legend, false, false);
        };

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setRangeGridlinePaint(new Color(0xE2, 0xE2, 0xE2));
        plot.setDomainGridlinesVisible(false);
        plot.setAxisOffset(new RectangleInsets(0, 0, 0, 0));

        CategoryAxis domain = plot.getDomainAxis();
        domain.setLabelFont(new Font(family, Font.PLAIN, 12));
        domain.setLabelPaint(new Color(0x44, 0x44, 0x44));
        domain.setTickLabelFont(new Font(family, Font.PLAIN, 10));
        domain.setTickLabelPaint(new Color(0x55, 0x55, 0x55));
        domain.setTickMarksVisible(false);

        ValueAxis range = plot.getRangeAxis();
        range.setLabelFont(new Font(family, Font.PLAIN, 12));
        range.setLabelPaint(new Color(0x44, 0x44, 0x44));
        range.setTickLabelFont(new Font(family, Font.PLAIN, 10));
        range.setTickLabelPaint(new Color(0x55, 0x55, 0x55));
        range.setAxisLinePaint(new Color(0xCC, 0xCC, 0xCC));
        range.setTickMarksVisible(false);
        if (range instanceof NumberAxis numberAxis) {
            numberAxis.setStandardTickUnits(NumberAxis.createStandardTickUnits());
        }

        List<Color> colors = palette(spec);
        if (plot.getRenderer() instanceof BarRenderer bar) {
            bar.setBarPainter(new StandardBarPainter());
            bar.setShadowVisible(false);
            bar.setDrawBarOutline(false);
            bar.setItemMargin(0.04);
        } else if (plot.getRenderer() instanceof LineAndShapeRenderer line) {
            line.setDefaultStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            line.setDefaultShapesVisible(true);
            line.setDefaultShapesFilled(true);
            line.setDefaultShape(new Ellipse2D.Double(-3, -3, 6, 6));
            line.setUseFillPaint(false);
        }
        for (int i = 0; i < dataset.getRowCount(); i++) {
            plot.getRenderer().setSeriesPaint(i, colors.get(i % colors.size()));
            plot.getRenderer().setSeriesStroke(i, new BasicStroke(2.2f));
        }
        return chart;
    }

    private JFreeChart createPie(ChartSpec spec, String kind, String family) {
        ChartSeries series = spec.series().getFirst();
        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
        for (int i = 0; i < spec.categories().size() && i < series.values().size(); i++) {
            Double value = series.values().get(i);
            if (value != null && Double.isFinite(value) && value >= 0) {
                dataset.setValue(spec.categories().get(i), value);
            }
        }
        if (dataset.getItemCount() == 0) {
            throw new IllegalArgumentException("The chart has no usable data values");
        }
        String title = spec.title() == null || spec.title().isBlank() ? null : spec.title();
        JFreeChart chart = kind.equals("doughnut")
                ? ChartFactory.createRingChart(title, dataset, spec.legend(), false, false)
                : ChartFactory.createPieChart(title, dataset, spec.legend(), false, false);

        PiePlot<?> plot = (PiePlot<?>) chart.getPlot();
        plot.setBackgroundPaint(null);
        plot.setOutlineVisible(false);
        plot.setShadowPaint(null);
        plot.setLabelFont(new Font(family, Font.PLAIN, 10));
        plot.setLabelPaint(new Color(0x33, 0x33, 0x33));
        plot.setLabelBackgroundPaint(null);
        plot.setLabelOutlinePaint(null);
        plot.setLabelShadowPaint(null);
        plot.setLabelGenerator(new StandardPieSectionLabelGenerator("{0} ({2})"));
        plot.setSectionOutlinesVisible(false);
        plot.setInteriorGap(0.04);
        if (plot instanceof RingPlot ring) {
            ring.setSectionDepth(0.48);
            ring.setSeparatorsVisible(false);
        }

        List<Color> colors = palette(spec);
        for (int i = 0; i < spec.categories().size(); i++) {
            if (dataset.getKeys().contains(spec.categories().get(i))) {
                plot.setSectionPaint(spec.categories().get(i), colors.get(i % colors.size()));
            }
        }
        return chart;
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
