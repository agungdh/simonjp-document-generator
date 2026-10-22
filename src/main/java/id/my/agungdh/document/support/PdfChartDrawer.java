package id.my.agungdh.document.support;

import id.my.agungdh.document.model.ChartSeries;
import id.my.agungdh.document.model.ChartSpec;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.util.Matrix;

import java.awt.Color;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws charts directly onto a PDF page as vectors: no intermediate image and
 * no external chart renderer. Supports the same chart types as the Office
 * renderers.
 */
public final class PdfChartDrawer {

    private static final Color GRID = new Color(0xE4, 0xE8, 0xEC);
    private static final Color AXIS = new Color(0x9A, 0xA5, 0xB1);
    private static final Color TEXT = new Color(0x33, 0x33, 0x33);
    private static final Color MUTED = new Color(0x60, 0x6B, 0x76);
    private static final DecimalFormat NUMBER = new DecimalFormat("0.##", DecimalFormatSymbolsHolder.SYMBOLS);

    private final PdfFonts fonts;

    public PdfChartDrawer(PdfFonts fonts) {
        this.fonts = fonts;
    }

    public void draw(PDPageContentStream cs, ChartSpec spec, float x, float y, float width, float height)
            throws IOException {
        String kind = ChartKinds.normalize(spec.type());
        List<Color> palette = palette(spec);
        float titleHeight = spec.title() == null || spec.title().isBlank() ? 0 : 24;
        float legendWidth = spec.legend() ? Math.min(108, Math.max(64, width * 0.26f)) : 8;
        float top = y + height - 4;

        if (titleHeight > 0) {
            PDFont titleFont = fonts.font(spec.fontFamily(), true, false);
            float size = 12;
            String title = fit(spec.title(), titleFont, size, width - 12);
            text(cs, titleFont, size, x + (width - width(titleFont, size, title)) / 2f,
                    y + height - 16, title, TEXT);
            top -= titleHeight;
        }

        if (ChartKinds.isPie(kind)) {
            drawPie(cs, spec, palette, x, y, width, height, top, legendWidth);
        } else if ("horizontalbar".equals(kind)) {
            drawBars(cs, spec, palette, kind, x, y, width, top, legendWidth, true);
        } else if ("line".equals(kind) || "area".equals(kind) || "stackedarea".equals(kind)) {
            drawLineOrArea(cs, spec, palette, kind, x, y, width, top, legendWidth);
        } else {
            drawBars(cs, spec, palette, kind, x, y, width, top, legendWidth, false);
        }

        if (spec.legend()) {
            float legendX = x + width - legendWidth + 8;
            if (ChartKinds.isPie(kind)) {
                legend(cs, spec.categories(), categoryColors(spec, palette),
                        legendX, y + top - y - 6, legendWidth - 12, spec.fontFamily());
            } else {
                List<String> names = spec.series().stream().map(ChartSeries::name).toList();
                legend(cs, names, seriesColors(spec, palette), legendX, top - 6, legendWidth - 12,
                        spec.fontFamily());
            }
        }
    }

    private void drawBars(PDPageContentStream cs, ChartSpec spec, List<Color> palette, String kind,
                          float x, float y, float width, float top, float legendWidth,
                          boolean horizontal) throws IOException {
        boolean stacked = "stackedbar".equals(kind);
        List<Double> values = values(spec, stacked);
        Scale scale = Scale.of(values, stacked || !"line".equals(kind));

        float left = x + (horizontal ? 72 : 46);
        float plotRight = x + width - legendWidth - 10;
        float bottom = y + 32;
        float plotWidth = Math.max(30, plotRight - left);
        float plotHeight = Math.max(30, top - bottom);

        grid(cs, scale, left, bottom, plotWidth, plotHeight, horizontal, spec.fontFamily());
        axes(cs, left, bottom, plotWidth, plotHeight);

        PDFont font = fonts.font(spec.fontFamily(), false, false);
        float labelSize = 7.5f;
        int categories = spec.categories().size();
        List<ChartSeries> series = spec.series();

        if (horizontal) {
            float band = plotHeight / categories;
            float cluster = band * 0.72f;
            float barHeight = stacked ? cluster : cluster / Math.max(1, series.size());
            for (int c = 0; c < categories; c++) {
                double cumulative = 0;
                for (int s = 0; s < series.size(); s++) {
                    double value = value(series.get(s), c);
                    double start = stacked ? cumulative : Math.min(0, scale.min());
                    float x1 = left + (float) ((Math.min(start, start + value) - scale.min()) / scale.range() * plotWidth);
                    float x2 = left + (float) ((Math.max(start, start + value) - scale.min()) / scale.range() * plotWidth);
                    float yy = stacked
                            ? bottom + (categories - 1 - c) * band + (band - cluster) / 2
                            : bottom + (categories - 1 - c) * band + (band - cluster) / 2 + s * barHeight;
                    fillRect(cs, x1, yy, Math.max(0.6f, x2 - x1), barHeight - 1,
                            palette.get(s % palette.size()));
                    if (stacked) {
                        cumulative += value;
                    }
                }
            }
            for (int c = 0; c < categories; c++) {
                String label = fit(spec.categories().get(c), font, labelSize, left - x - 8);
                float cy = bottom + (categories - 1 - c) * band + band / 2;
                text(cs, font, labelSize, x + 4, cy - labelSize * 0.35f, label, MUTED);
            }
            if (spec.xAxisTitle() != null && !spec.xAxisTitle().isBlank()) {
                String axisTitle = fit(spec.xAxisTitle(), font, 8, plotWidth);
                text(cs, font, 8, left + (plotWidth - width(font, 8, axisTitle)) / 2f, y + 14, axisTitle, MUTED);
            }
            return;
        }

        float band = plotWidth / categories;
        float cluster = band * 0.72f;
        float barWidth = stacked ? cluster : cluster / Math.max(1, series.size());
        float zero = bottom + (float) ((0 - scale.min()) / scale.range() * plotHeight);
        for (int c = 0; c < categories; c++) {
            double cumulative = 0;
            for (int s = 0; s < series.size(); s++) {
                double value = value(series.get(s), c);
                float heightPx = (float) (Math.abs(value) / scale.range() * plotHeight);
                float xx = stacked
                        ? left + c * band + (band - cluster) / 2
                        : left + c * band + (band - cluster) / 2 + s * barWidth;
                float yy = stacked
                        ? (value >= 0
                            ? zero + (float) (cumulative / scale.range() * plotHeight)
                            : zero - (float) ((cumulative + Math.abs(value)) / scale.range() * plotHeight))
                        : (value >= 0 ? zero : zero - heightPx);
                fillRect(cs, xx, yy, Math.max(0.6f, barWidth - 1), heightPx, palette.get(s % palette.size()));
                if (stacked) {
                    cumulative += value;
                }
            }
        }
        categoryLabels(cs, spec, font, labelSize, left, bottom, band, categories);
        xAxisTitle(cs, spec, x, y, left, plotWidth, font);
        yAxisTitle(cs, spec, x, bottom, plotHeight, font);
    }

    private void drawLineOrArea(PDPageContentStream cs, ChartSpec spec, List<Color> palette, String kind,
                                float x, float y, float width, float top, float legendWidth)
            throws IOException {
        boolean area = "area".equals(kind) || "stackedarea".equals(kind);
        boolean stacked = "stackedarea".equals(kind);
        List<Double> allValues = values(spec, stacked);
        Scale scale = Scale.of(allValues, false);

        float left = x + 46;
        float plotRight = x + width - legendWidth - 10;
        float bottom = y + 32;
        float plotWidth = Math.max(30, plotRight - left);
        float plotHeight = Math.max(30, top - bottom);

        grid(cs, scale, left, bottom, plotWidth, plotHeight, false, spec.fontFamily());
        axes(cs, left, bottom, plotWidth, plotHeight);

        PDFont font = fonts.font(spec.fontFamily(), false, false);
        float labelSize = 7.5f;
        int categories = spec.categories().size();
        List<ChartSeries> series = spec.series();
        float band = plotWidth / Math.max(1, categories);
        float zero = bottom + (float) ((0 - scale.min()) / scale.range() * plotHeight);

        if (area) {
            double[] cumulative = new double[categories];
            for (int s = 0; s < series.size(); s++) {
                float[] xs = new float[categories];
                float[] ys = new float[categories];
                float[] base = new float[categories];
                for (int c = 0; c < categories; c++) {
                    double value = value(series.get(s), c);
                    double lower = stacked ? cumulative[c] : 0;
                    double upper = stacked ? lower + value : value;
                    xs[c] = left + band * (c + 0.5f);
                    ys[c] = bottom + (float) ((upper - scale.min()) / scale.range() * plotHeight);
                    base[c] = bottom + (float) ((lower - scale.min()) / scale.range() * plotHeight);
                    if (stacked) {
                        cumulative[c] = upper;
                    }
                }
                Color color = palette.get(s % palette.size());
                pathPolygon(cs, xs, ys, base, color);
                strokeSeries(cs, xs, ys, color);
            }
        } else {
            for (int s = 0; s < series.size(); s++) {
                float[] xs = new float[categories];
                float[] ys = new float[categories];
                for (int c = 0; c < categories; c++) {
                    xs[c] = left + band * (c + 0.5f);
                    ys[c] = bottom + (float) ((value(series.get(s), c) - scale.min()) / scale.range() * plotHeight);
                }
                Color color = palette.get(s % palette.size());
                strokeSeries(cs, xs, ys, color);
                for (int c = 0; c < categories; c++) {
                    dot(cs, xs[c], ys[c], 2.2f, color);
                }
            }
        }

        categoryLabels(cs, spec, font, labelSize, left, bottom, band, categories);
        xAxisTitle(cs, spec, x, y, left, plotWidth, font);
        yAxisTitle(cs, spec, x, bottom, plotHeight, font);
    }

    private void drawPie(PDPageContentStream cs, ChartSpec spec, List<Color> palette,
                         float x, float y, float width, float height, float top, float legendWidth)
            throws IOException {
        float left = x + 8;
        float right = x + width - legendWidth - 8;
        float bottom = y + 8;
        float plotWidth = Math.max(30, right - left);
        float plotHeight = Math.max(30, top - bottom);
        float cx = left + plotWidth / 2;
        float cy = bottom + plotHeight / 2;
        float radius = Math.min(plotWidth, plotHeight) * 0.44f;
        if (radius < 10) {
            radius = 10;
        }

        List<ChartSeries> series = spec.series();
        List<String> categories = spec.categories();
        double total = 0;
        for (int c = 0; c < categories.size(); c++) {
            total += Math.max(0, value(series.getFirst(), c));
        }
        if (total <= 0) {
            PDFont font = fonts.font(spec.fontFamily(), false, false);
            String text = "No data";
            text(cs, font, 10, cx - width(font, 10, text) / 2, cy, text, MUTED);
            return;
        }

        PDFont font = fonts.font(spec.fontFamily(), false, false);
        float start = 90;
        boolean doughnut = "doughnut".equals(ChartKinds.normalize(spec.type()));
        float inner = radius * 0.56f;
        for (int c = 0; c < categories.size(); c++) {
            double value = Math.max(0, value(series.getFirst(), c));
            float sweep = (float) (-value / total * 360);
            Color color = palette.get(c % palette.size());
            if (doughnut) {
                doughnutSlice(cs, cx, cy, radius, inner, start, sweep, color);
            } else {
                pieSlice(cs, cx, cy, radius, start, sweep, color);
            }
            double mid = Math.toRadians(start + sweep / 2);
            float dirX = (float) Math.cos(mid);
            float dirY = (float) Math.sin(mid);
            float labelRadius = radius * 1.12f;
            float lx = cx + dirX * labelRadius;
            float ly = cy + dirY * labelRadius;
            float pct = (float) (value / total * 100);
            String label = categories.get(c) + " " + Math.round(pct) + "%";
            float size = 7.4f;
            label = fit(label, font, size, Math.max(40, width / 2.2f));
            float tw = width(font, size, label);
            float tx = dirX >= 0 ? lx + 3 : lx - tw - 3;
            float ty = ly - size * 0.35f;
            cs.setStrokingColor(new Color(0xB0, 0xB8, 0xC0));
            cs.setLineWidth(0.5f);
            cs.moveTo(cx + dirX * radius * 1.02f, cy + dirY * radius * 1.02f);
            cs.lineTo(lx, ly);
            cs.stroke();
            text(cs, font, size, tx, ty, label, TEXT);
            start += sweep;
        }
    }

    private void pieSlice(PDPageContentStream cs, float cx, float cy, float radius,
                          float start, float sweep, Color color) throws IOException {
        cs.setNonStrokingColor(color);
        cs.moveTo(cx, cy);
        cs.lineTo(cx + radius * cos(start), cy + radius * sin(start));
        arc(cs, cx, cy, radius, radius, start, sweep);
        cs.closePath();
        cs.fill();
    }

    private void doughnutSlice(PDPageContentStream cs, float cx, float cy, float outer, float inner,
                               float start, float sweep, Color color) throws IOException {
        float end = start + sweep;
        cs.setNonStrokingColor(color);
        cs.moveTo(cx + outer * cos(start), cy + outer * sin(start));
        arc(cs, cx, cy, outer, outer, start, sweep);
        cs.lineTo(cx + inner * cos(end), cy + inner * sin(end));
        arc(cs, cx, cy, inner, inner, end, -sweep);
        cs.closePath();
        cs.fillEvenOdd();
    }

    private void arc(PDPageContentStream cs, float cx, float cy, float rx, float ry,
                     float startDegrees, float sweepDegrees) throws IOException {
        int segments = Math.max(1, (int) Math.ceil(Math.abs(sweepDegrees) / 90.0));
        float delta = sweepDegrees / segments;
        float angle = startDegrees;
        for (int i = 0; i < segments; i++) {
            float next = angle + delta;
            float k = (float) (4.0 / 3.0 * Math.tan(Math.toRadians(delta) / 4.0));
            float x1 = cx + rx * cos(angle) - k * rx * sin(angle);
            float y1 = cy + ry * sin(angle) + k * ry * cos(angle);
            float x2 = cx + rx * cos(next) + k * rx * sin(next);
            float y2 = cy + ry * sin(next) - k * ry * cos(next);
            cs.curveTo(x1, y1, x2, y2, cx + rx * cos(next), cy + ry * sin(next));
            angle = next;
        }
    }

    private void grid(PDPageContentStream cs, Scale scale, float left, float bottom,
                      float plotWidth, float plotHeight, boolean horizontal, String family)
            throws IOException {
        PDFont font = fonts.font(family, false, false);
        float size = 7.5f;
        cs.setLineWidth(0.5f);
        for (int i = 0; i <= scale.ticks(); i++) {
            double value = scale.min() + i * scale.step();
            float position = (float) ((value - scale.min()) / scale.range());
            String label = NUMBER.format(value);
            if (horizontal) {
                float x = left + position * plotWidth;
                cs.setStrokingColor(GRID);
                cs.moveTo(x, bottom);
                cs.lineTo(x, bottom + plotHeight);
                cs.stroke();
                text(cs, font, size, x - width(font, size, label) / 2f, bottom - 11, label, MUTED);
            } else {
                float y = bottom + position * plotHeight;
                cs.setStrokingColor(GRID);
                cs.moveTo(left, y);
                cs.lineTo(left + plotWidth, y);
                cs.stroke();
                text(cs, font, size, left - 5 - width(font, size, label), y - 2.5f, label, MUTED);
            }
        }
    }

    private void axes(PDPageContentStream cs, float left, float bottom, float width, float height)
            throws IOException {
        cs.setStrokingColor(AXIS);
        cs.setLineWidth(0.7f);
        cs.moveTo(left, bottom);
        cs.lineTo(left, bottom + height);
        cs.moveTo(left, bottom);
        cs.lineTo(left + width, bottom);
        cs.stroke();
    }

    private void categoryLabels(PDPageContentStream cs, ChartSpec spec, PDFont font, float size,
                                float left, float bottom, float band, int categories) throws IOException {
        for (int c = 0; c < categories; c++) {
            String label = fit(spec.categories().get(c), font, size, band - 2);
            float x = left + band * (c + 0.5f);
            text(cs, font, size, x - width(font, size, label) / 2f, bottom - 11, label, MUTED);
        }
    }

    private void xAxisTitle(PDPageContentStream cs, ChartSpec spec, float x, float y,
                            float left, float plotWidth, PDFont font) throws IOException {
        if (spec.xAxisTitle() == null || spec.xAxisTitle().isBlank()) {
            return;
        }
        String title = fit(spec.xAxisTitle(), font, 8, plotWidth);
        text(cs, font, 8, left + (plotWidth - width(font, 8, title)) / 2f, y + 14, title, MUTED);
    }

    private void yAxisTitle(PDPageContentStream cs, ChartSpec spec, float x, float bottom,
                            float plotHeight, PDFont font) throws IOException {
        if (spec.yAxisTitle() == null || spec.yAxisTitle().isBlank()) {
            return;
        }
        rotatedText(cs, font, 8, x + 10, bottom + plotHeight / 2f, spec.yAxisTitle(), MUTED);
    }

    private void rotatedText(PDPageContentStream cs, PDFont font, float size, float x, float centerY,
                             String value, Color color) throws IOException {
        String text = fit(value, font, size, 180);
        float width = width(font, size, text);
        cs.saveGraphicsState();
        cs.beginText();
        cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(-90), x, centerY - width / 2));
        cs.setFont(font, size);
        cs.setNonStrokingColor(color);
        cs.showText(text);
        cs.endText();
        cs.restoreGraphicsState();
    }

    private void legend(PDPageContentStream cs, List<String> labels, List<Color> colors,
                        float x, float top, float width, String family) throws IOException {
        PDFont font = fonts.font(family, false, false);
        float size = 7.6f;
        float y = top;
        for (int i = 0; i < labels.size() && y > 8; i++) {
            Color color = colors.get(i % colors.size());
            cs.setNonStrokingColor(color);
            cs.addRect(x, y - 6.5f, 7, 7);
            cs.fill();
            String label = fit(labels.get(i), font, size, width - 12);
            text(cs, font, size, x + 11, y - 6, label, TEXT);
            y -= 13;
        }
    }

    private void pathPolygon(PDPageContentStream cs, float[] xs, float[] ys, float[] base, Color color)
            throws IOException {
        if (xs.length == 0) {
            return;
        }
        cs.setNonStrokingColor(color);
        cs.moveTo(xs[0], base[0]);
        for (int i = 0; i < xs.length; i++) {
            cs.lineTo(xs[i], ys[i]);
        }
        for (int i = xs.length - 1; i >= 0; i--) {
            cs.lineTo(xs[i], base[i]);
        }
        cs.closePath();
        cs.fill();
    }

    private void strokeSeries(PDPageContentStream cs, float[] xs, float[] ys, Color color)
            throws IOException {
        if (xs.length == 0) {
            return;
        }
        cs.setStrokingColor(color);
        cs.setLineWidth(1.8f);
        cs.setLineJoinStyle(1);
        cs.moveTo(xs[0], ys[0]);
        for (int i = 1; i < xs.length; i++) {
            cs.lineTo(xs[i], ys[i]);
        }
        cs.stroke();
    }

    private void dot(PDPageContentStream cs, float x, float y, float radius, Color color)
            throws IOException {
        float k = radius * 0.5523f;
        cs.setNonStrokingColor(color);
        cs.moveTo(x + radius, y);
        cs.curveTo(x + radius, y + k, x + k, y + radius, x, y + radius);
        cs.curveTo(x - k, y + radius, x - radius, y + k, x - radius, y);
        cs.curveTo(x - radius, y - k, x - k, y - radius, x, y - radius);
        cs.curveTo(x + k, y - radius, x + radius, y - k, x + radius, y);
        cs.closePath();
        cs.fill();
    }

    private void fillRect(PDPageContentStream cs, float x, float y, float width, float height, Color color)
            throws IOException {
        cs.setNonStrokingColor(color);
        cs.addRect(x, y, width, height);
        cs.fill();
    }

    private void text(PDPageContentStream cs, PDFont font, float size, float x, float baseline,
                      String value, Color color) throws IOException {
        if (value == null || value.isEmpty()) {
            return;
        }
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(x, baseline);
        cs.showText(value);
        cs.endText();
    }

    private float width(PDFont font, float size, String value) {
        return fonts.width(font, size, value);
    }

    private String fit(String value, PDFont font, float size, float maxWidth) {
        if (value == null || value.isEmpty() || maxWidth <= 0) {
            return value == null ? "" : value;
        }
        if (width(font, size, value) <= maxWidth) {
            return value;
        }
        String ellipsis = "...";
        float available = maxWidth - width(font, size, ellipsis);
        if (available <= 0) {
            return "";
        }
        int end = value.length();
        while (end > 0 && width(font, size, value.substring(0, end)) > available) {
            end--;
        }
        return end == 0 ? "" : value.substring(0, end) + ellipsis;
    }

    private static double value(ChartSeries series, int index) {
        if (series == null || index >= series.values().size()) {
            return 0;
        }
        Double value = series.values().get(index);
        return value == null || !Double.isFinite(value) ? 0 : value;
    }

    private List<Double> values(ChartSpec spec, boolean stacked) {
        int categories = spec.categories().size();
        List<Double> result = new ArrayList<>();
        if (stacked) {
            for (int c = 0; c < categories; c++) {
                double sum = 0;
                for (ChartSeries series : spec.series()) {
                    sum += value(series, c);
                }
                result.add(sum);
            }
            result.add(0.0);
        } else {
            for (ChartSeries series : spec.series()) {
                for (int c = 0; c < categories; c++) {
                    result.add(value(series, c));
                }
            }
            result.add(0.0);
        }
        return result;
    }

    private List<Color> palette(ChartSpec spec) {
        List<String> requested = spec.palette().isEmpty() ? Styles.PALETTE : spec.palette();
        List<Color> colors = new ArrayList<>();
        for (String value : requested) {
            String hex = Colors.toHexOrNull(value);
            if (hex != null) {
                colors.add(Colors.parse(hex));
            }
        }
        if (colors.isEmpty()) {
            for (String value : Styles.PALETTE) {
                colors.add(Colors.parse(value));
            }
        }
        return colors;
    }

    private List<Color> seriesColors(ChartSpec spec, List<Color> palette) {
        List<Color> colors = new ArrayList<>();
        for (int i = 0; i < spec.series().size(); i++) {
            colors.add(palette.get(i % palette.size()));
        }
        return colors;
    }

    private List<Color> categoryColors(ChartSpec spec, List<Color> palette) {
        List<Color> colors = new ArrayList<>();
        for (int i = 0; i < spec.categories().size(); i++) {
            colors.add(palette.get(i % palette.size()));
        }
        return colors;
    }

    private static float cos(float degrees) {
        return (float) Math.cos(Math.toRadians(degrees));
    }

    private static float sin(float degrees) {
        return (float) Math.sin(Math.toRadians(degrees));
    }

    private record Scale(double min, double max, double step, int ticks) {

        static Scale of(List<Double> values, boolean includeZero) {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (Double value : values) {
                if (value == null || !Double.isFinite(value)) {
                    continue;
                }
                min = Math.min(min, value);
                max = Math.max(max, value);
                if (includeZero) {
                    min = Math.min(min, 0);
                    max = Math.max(max, 0);
                }
            }
            if (!Double.isFinite(min) || !Double.isFinite(max)) {
                min = 0;
                max = 1;
            }
            if (min == max) {
                if (min == 0) {
                    max = 1;
                } else {
                    min = Math.min(0, min);
                    max = max + Math.abs(max) * 0.2;
                }
            }
            int ticks = 5;
            double range = niceNumber(max - min, false);
            double step = niceNumber(range / (ticks - 1), true);
            min = Math.floor(min / step) * step;
            max = Math.ceil(max / step) * step;
            if (max == min) {
                max = min + step;
            }
            int count = (int) Math.round((max - min) / step);
            return new Scale(min, max, step, Math.max(1, count));
        }

        double range() {
            return max - min;
        }

        private static double niceNumber(double value, boolean round) {
            if (value <= 0 || !Double.isFinite(value)) {
                return 1;
            }
            double exponent = Math.floor(Math.log10(value));
            double fraction = value / Math.pow(10, exponent);
            double nice;
            if (round) {
                if (fraction < 1.5) {
                    nice = 1;
                } else if (fraction < 3) {
                    nice = 2;
                } else if (fraction < 7) {
                    nice = 5;
                } else {
                    nice = 10;
                }
            } else {
                if (fraction <= 1) {
                    nice = 1;
                } else if (fraction <= 2) {
                    nice = 2;
                } else if (fraction <= 5) {
                    nice = 5;
                } else {
                    nice = 10;
                }
            }
            return nice * Math.pow(10, exponent);
        }
    }

    private static final class DecimalFormatSymbolsHolder {
        private static final java.text.DecimalFormatSymbols SYMBOLS =
                java.text.DecimalFormatSymbols.getInstance(Locale.ROOT);
    }
}
