package id.my.agungdh.document.service;

import id.my.agungdh.document.model.ChartBlock;
import id.my.agungdh.document.model.ChartSeries;
import id.my.agungdh.document.model.ChartSpec;
import id.my.agungdh.document.model.DocumentBlock;
import id.my.agungdh.document.model.DocumentRequest;
import id.my.agungdh.document.model.HeadingBlock;
import id.my.agungdh.document.model.ImageBlock;
import id.my.agungdh.document.model.ParagraphBlock;
import id.my.agungdh.document.model.TableBlock;
import id.my.agungdh.document.model.TextRun;
import id.my.agungdh.document.support.ChartKinds;
import id.my.agungdh.document.support.Styles;

import java.util.List;

/** Rejects malformed requests with a helpful 400 message before any rendering starts. */
public final class DocumentValidator {

    private static final int MAX_BLOCKS = 300;
    private static final int MAX_TABLE_COLUMNS = 12;
    private static final int MAX_TABLE_ROWS = 2000;
    private static final int MAX_CHART_CATEGORIES = 200;
    private static final int MAX_CHART_SERIES = 20;
    private static final long MAX_CHARACTERS = 2_000_000;

    public void validate(DocumentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.title().length() > 300) {
            throw new IllegalArgumentException("'title' must be 300 characters or fewer");
        }
        if (request.blocks().size() > MAX_BLOCKS) {
            throw new IllegalArgumentException("A document can contain at most " + MAX_BLOCKS + " blocks");
        }
        long characters = 0;
        for (int i = 0; i < request.blocks().size(); i++) {
            DocumentBlock block = request.blocks().get(i);
            if (block == null) {
                throw new IllegalArgumentException("Block " + i + " is null");
            }
            characters += validateBlock(block, i);
        }
        if (characters > MAX_CHARACTERS) {
            throw new IllegalArgumentException("Document text is too large (max 2,000,000 characters)");
        }
    }

    private long validateBlock(DocumentBlock block, int index) {
        if (block instanceof HeadingBlock heading) {
            checkSize(heading.fontSize(), "blocks[" + index + "].fontSize");
            return heading.text().length();
        }
        if (block instanceof ParagraphBlock paragraph) {
            checkSize(paragraph.fontSize(), "blocks[" + index + "].fontSize");
            if (paragraph.lineSpacing() < 0.5 || paragraph.lineSpacing() > 4.0) {
                throw new IllegalArgumentException("blocks[" + index + "].lineSpacing must be between 0.5 and 4");
            }
            long characters = 0;
            for (TextRun run : paragraph.runs()) {
                checkSize(run.fontSize(), "blocks[" + index + "].runs[].fontSize");
                characters += run.text().length();
            }
            return characters;
        }
        if (block instanceof TableBlock table) {
            int columns = table.columnCount();
            if (columns == 0) {
                throw new IllegalArgumentException("blocks[" + index + "]: a table needs at least one column");
            }
            if (columns > MAX_TABLE_COLUMNS) {
                throw new IllegalArgumentException("blocks[" + index + "]: a table can have at most "
                        + MAX_TABLE_COLUMNS + " columns");
            }
            if (table.rows().size() > MAX_TABLE_ROWS) {
                throw new IllegalArgumentException("blocks[" + index + "]: a table can have at most "
                        + MAX_TABLE_ROWS + " rows");
            }
            checkSize(table.fontSize(), "blocks[" + index + "].fontSize");
            long characters = 0;
            for (String header : table.headers()) {
                characters += header == null ? 0 : header.length();
            }
            for (List<String> row : table.rows()) {
                for (String value : row) {
                    characters += value == null ? 0 : value.length();
                }
            }
            return characters;
        }
        if (block instanceof ImageBlock image) {
            if (image.src() == null || image.src().isBlank()) {
                throw new IllegalArgumentException("blocks[" + index + "].src is required for an image");
            }
            checkDimension(image.width(), "blocks[" + index + "].width");
            checkDimension(image.height(), "blocks[" + index + "].height");
            return 0;
        }
        if (block instanceof ChartBlock chartBlock) {
            ChartSpec chart = chartBlock.chart();
            if (chart == null) {
                throw new IllegalArgumentException("blocks[" + index + "].chart is required");
            }
            validateChart(chart, index);
            checkDimension(chartBlock.width(), "blocks[" + index + "].width");
            checkDimension(chartBlock.height(), "blocks[" + index + "].height");
            return 0;
        }
        return 0;
    }

    private void validateChart(ChartSpec chart, int index) {
        String kind = ChartKinds.normalize(chart.type());
        if (!ChartKinds.SUPPORTED.contains(kind)) {
            throw new IllegalArgumentException("blocks[" + index + "].chart.type must be one of: "
                    + String.join(", ", ChartKinds.SUPPORTED));
        }
        if (chart.categories().isEmpty()) {
            throw new IllegalArgumentException("blocks[" + index + "].chart.categories must not be empty");
        }
        if (chart.categories().size() > MAX_CHART_CATEGORIES) {
            throw new IllegalArgumentException("blocks[" + index + "] chart: at most "
                    + MAX_CHART_CATEGORIES + " categories are supported");
        }
        if (chart.series().isEmpty()) {
            throw new IllegalArgumentException("blocks[" + index + "].chart.series must not be empty");
        }
        if (chart.series().size() > MAX_CHART_SERIES) {
            throw new IllegalArgumentException("blocks[" + index + "] chart: at most "
                    + MAX_CHART_SERIES + " series are supported");
        }
        for (int s = 0; s < chart.series().size(); s++) {
            ChartSeries series = chart.series().get(s);
            if (series.values().size() != chart.categories().size()) {
                throw new IllegalArgumentException("blocks[" + index + "].chart.series[" + s
                        + "].values must contain exactly " + chart.categories().size() + " numbers");
            }
            for (Double value : series.values()) {
                if (value != null && !Double.isFinite(value)) {
                    throw new IllegalArgumentException("blocks[" + index + "].chart.series[" + s
                            + "] contains a non-finite value");
                }
            }
        }
    }

    private void checkSize(Double size, String field) {
        if (size != null && (size < 4 || size > 200)) {
            throw new IllegalArgumentException(field + " must be between 4 and 200 points");
        }
    }

    private void checkDimension(Double value, String field) {
        if (value != null && (value < 10 || value > 2000)) {
            throw new IllegalArgumentException(field + " must be between 10 and 2000 points");
        }
    }
}
