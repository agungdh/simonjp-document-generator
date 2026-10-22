package id.my.agungdh.document.render;

import id.my.agungdh.document.model.ChartBlock;
import id.my.agungdh.document.model.DocumentBlock;
import id.my.agungdh.document.model.DocumentRequest;
import id.my.agungdh.document.model.HeadingBlock;
import id.my.agungdh.document.model.ImageBlock;
import id.my.agungdh.document.model.PageBreakBlock;
import id.my.agungdh.document.model.ParagraphBlock;
import id.my.agungdh.document.model.TableBlock;
import id.my.agungdh.document.model.TextRun;
import id.my.agungdh.document.support.Align;
import id.my.agungdh.document.support.Colors;
import id.my.agungdh.document.support.ImageLoader;
import id.my.agungdh.document.support.MediaSizing;
import id.my.agungdh.document.support.OfficeChartWriter;
import id.my.agungdh.document.support.PageSpec;
import id.my.agungdh.document.support.Styles;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.LineSpacingRule;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFChart;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Word (.docx) rendering with Apache POI, including native editable charts. */
public final class DocxDocumentRenderer {

    private final ImageLoader imageLoader = new ImageLoader();
    private final OfficeChartWriter chartWriter = new OfficeChartWriter();

    public byte[] render(DocumentRequest request) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024)) {

            PageSpec page = PageSpec.of(request);
            applyPageSetup(document, request, page);
            applyMetadata(document, request);

            boolean afterTable = false;
            for (DocumentBlock block : request.blocks()) {
                if (block instanceof HeadingBlock heading) {
                    heading(document, heading, afterTable ? 120 : -1);
                } else if (block instanceof ParagraphBlock paragraph) {
                    paragraph(document, paragraph, afterTable ? 120 : 0);
                } else if (block instanceof TableBlock table) {
                    table(document, table);
                } else if (block instanceof ImageBlock image) {
                    image(document, image, page);
                } else if (block instanceof ChartBlock chart) {
                    chart(document, chart, page);
                } else if (block instanceof PageBreakBlock) {
                    XWPFParagraph paragraph = document.createParagraph();
                    paragraph.setPageBreak(true);
                }
                afterTable = block instanceof TableBlock;
            }

            document.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not render DOCX: " + e.getMessage(), e);
        }
    }

    private void applyPageSetup(XWPFDocument document, DocumentRequest request, PageSpec page) {
        CTBody body = document.getDocument().getBody();
        CTSectPr sectPr = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        CTPageSz size = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        size.setW(BigInteger.valueOf(Math.round(page.width() * 20)));
        size.setH(BigInteger.valueOf(Math.round(page.height() * 20)));
        size.setOrient("landscape".equalsIgnoreCase(request.orientation())
                ? STPageOrientation.LANDSCAPE : STPageOrientation.PORTRAIT);

        CTPageMar margins = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        int margin = (int) Math.round(page.margin() * 20);
        margins.setTop(BigInteger.valueOf(margin));
        margins.setBottom(BigInteger.valueOf(margin));
        margins.setLeft(BigInteger.valueOf(margin));
        margins.setRight(BigInteger.valueOf(margin));
        margins.setHeader(BigInteger.valueOf(360));
        margins.setFooter(BigInteger.valueOf(360));
        margins.setGutter(BigInteger.ZERO);
    }

    private void applyMetadata(XWPFDocument document, DocumentRequest request) {
        var core = document.getProperties().getCoreProperties();
        core.setTitle(request.title());
        if (request.author() != null) {
            core.setCreator(request.author());
        }
        if (request.subject() != null) {
            core.setSubjectProperty(request.subject());
        }
        core.setDescription("Generated by SimonJP Document Generator");
    }

    private void heading(XWPFDocument document, HeadingBlock block, int spacingBefore) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(alignment(Align.from(block.align())));
        paragraph.setSpacingAfter((int) Math.round(block.marginAfter() * 20));
        paragraph.setSpacingBefore(spacingBefore >= 0 ? spacingBefore : (block.level() == 1 ? 0 : 220));
        paragraph.setKeepNext(true);

        String family = Styles.font(block.fontFamily() == null
                ? Styles.headingFont(block.level()) : block.fontFamily());
        double size = Styles.size(block.fontSize(), Styles.headingSize(block.level()));
        String color = Colors.normalize(block.color(), Colors.PRIMARY);
        addText(paragraph, block.text(), family, size, true, false, false, color);
    }

    private void paragraph(XWPFDocument document, ParagraphBlock block, int spacingBefore) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(alignment(Align.from(block.align())));
        paragraph.setSpacingAfter((int) Math.round(block.marginAfter() * 20));
        paragraph.setSpacingBefore(spacingBefore);
        paragraph.setSpacingBetween(block.lineSpacing(), LineSpacingRule.AUTO);

        String family = Styles.font(block.fontFamily());
        double size = Styles.size(block.fontSize(), Styles.BODY_SIZE);
        String color = Colors.normalize(block.color(), Colors.BODY);

        if (block.runs().isEmpty()) {
            XWPFRun run = paragraph.createRun();
            style(run, family, size, false, false, false, color);
            run.setText(" ");
            return;
        }
        for (TextRun text : block.runs()) {
            String runFamily = text.fontFamily() == null ? family : text.fontFamily();
            double runSize = Styles.size(text.fontSize(), size);
            String runColor = Colors.normalize(text.color(), color);
            addText(paragraph, text.text(), runFamily, runSize,
                    text.bold(), text.italic(), text.underline(), runColor);
        }
    }

    private void table(XWPFDocument document, TableBlock block) {
        int columns = block.columnCount();
        if (columns == 0) {
            return;
        }
        boolean hasHeader = !block.headers().isEmpty();
        int rows = block.rows().size() + (hasHeader ? 1 : 0);
        if (rows == 0) {
            return;
        }
        XWPFTable table = document.createTable(rows, columns);
        table.setWidth("100%");
        table.setTableAlignment(org.apache.poi.xwpf.usermodel.TableRowAlign.CENTER);
        table.setCellMargins(60, 60, 90, 90);

        String border = Colors.forPoi(block.borderColor(), Colors.BORDER);
        table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, border);
        table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, border);
        table.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, border);
        table.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, border);
        table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, border);
        table.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, border);

        String family = Styles.font(block.fontFamily());
        double size = Styles.size(block.fontSize(), Styles.TABLE_SIZE);
        String headerBg = Colors.forPoi(block.headerBackground(), Colors.PRIMARY);
        String headerColor = Colors.normalize(block.headerColor(), "#FFFFFF");
        String stripe = Colors.forPoi(block.stripeColor(), "#F2F2F2");
        List<Double> widths = columnPercentages(block, columns);
        Align align = Align.from(block.align());

        int rowIndex = 0;
        if (hasHeader) {
            XWPFTableRow row = table.getRow(0);
            row.setRepeatHeader(true);
            for (int c = 0; c < columns; c++) {
                String value = c < block.headers().size() ? block.headers().get(c) : "";
                cell(row, c, value, family, size, true, headerColor, headerBg, align, widths, border);
            }
            rowIndex = 1;
        }
        for (List<String> dataRow : block.rows()) {
            XWPFTableRow row = table.getRow(rowIndex);
            String background = rowIndex % 2 == 0 ? stripe : null;
            for (int c = 0; c < columns; c++) {
                String value = c < dataRow.size() ? dataRow.get(c) : "";
                cell(row, c, value, family, size, false, Colors.BODY, background, align, widths, border);
            }
            rowIndex++;
        }
    }

    private void cell(XWPFTableRow row, int column, String value, String family, double size,
                      boolean bold, String color, String background, Align align,
                      List<Double> widths, String border) {
        XWPFTableCell cell = row.getCell(column);
        if (cell == null) {
            cell = row.createCell();
        }
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        if (background != null) {
            cell.setColor(background);
        }
        cell.setWidth(String.format(Locale.ROOT, "%.2f%%", widths.get(column)));
        applyBorders(cell, border);

        XWPFParagraph paragraph = cell.getParagraphs().isEmpty()
                ? cell.addParagraph() : cell.getParagraphs().getFirst();
        paragraph.setAlignment(alignment(align));
        paragraph.setSpacingAfter(0);
        paragraph.setSpacingBefore(0);
        paragraph.setSpacingBetween(1.0);
        addText(paragraph, value, family, size, bold, false, false, color);
    }

    private void applyBorders(XWPFTableCell cell, String borderColor) {
        CTTcPr properties = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTcBorders borders = properties.isSetTcBorders() ? properties.getTcBorders() : properties.addNewTcBorders();
        for (var side : List.of(
                borders.addNewTop(), borders.addNewBottom(),
                borders.addNewLeft(), borders.addNewRight())) {
            side.setVal(STBorder.SINGLE);
            side.setSz(BigInteger.valueOf(4));
            side.setSpace(BigInteger.ZERO);
            side.setColor(borderColor);
        }
    }

    private void image(XWPFDocument document, ImageBlock block, PageSpec page) {
        ImageLoader.LoadedImage image = imageLoader.load(block.src());
        MediaSizing.Size size = MediaSizing.image(image.width(), image.height(),
                block.width(), block.height(), page.contentWidth(), page.contentHeight() - 40);
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(alignment(Align.from(block.align())));
        paragraph.setSpacingAfter(block.caption() == null || block.caption().isBlank() ? 220 : 40);
        XWPFRun run = paragraph.createRun();
        try {
            run.addPicture(new ByteArrayInputStream(image.png()), Document.PICTURE_TYPE_PNG,
                    "image.png", toEmu(size.width()), toEmu(size.height()));
        } catch (Exception e) {
            throw new IllegalStateException("Could not embed image in DOCX: " + e.getMessage(), e);
        }
        caption(document, block.caption());
    }

    private void chart(XWPFDocument document, ChartBlock block, PageSpec page) {
        if (block.chart() == null) {
            throw new IllegalArgumentException("Chart block is missing the 'chart' object");
        }
        MediaSizing.Size size = MediaSizing.chart(block, page.contentWidth(), page.contentHeight() - 40);
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(alignment(Align.from(block.align())));
        paragraph.setSpacingAfter(block.caption() == null || block.caption().isBlank() ? 220 : 40);
        XWPFRun run = paragraph.createRun();
        try {
            XWPFChart chart = document.createChart(run, toEmu(size.width()), toEmu(size.height()));
            chartWriter.populate(chart, block.chart());
        } catch (Exception e) {
            throw new IllegalStateException("Could not embed chart in DOCX: " + e.getMessage(), e);
        }
        caption(document, block.caption());
    }

    private void caption(XWPFDocument document, String caption) {
        if (caption == null || caption.isBlank()) {
            return;
        }
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(200);
        paragraph.setSpacingBefore(20);
        XWPFRun run = paragraph.createRun();
        style(run, Styles.DEFAULT_FONT, Styles.CAPTION_SIZE, false, true, false, Colors.CAPTION);
        run.setText(caption);
    }

    private void addText(XWPFParagraph paragraph, String text, String family, double size,
                         boolean bold, boolean italic, boolean underline, String color) {
        String[] lines = (text == null ? "" : text).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            XWPFRun run = paragraph.createRun();
            style(run, family, size, bold, italic, underline, color);
            if (i > 0) {
                run.addBreak();
            }
            run.setText(lines[i]);
        }
    }

    private void style(XWPFRun run, String family, double size,
                       boolean bold, boolean italic, boolean underline, String color) {
        run.setFontFamily(Styles.font(family));
        run.setFontSize(size);
        run.setBold(bold);
        run.setItalic(italic);
        run.setUnderline(underline ? UnderlinePatterns.SINGLE : UnderlinePatterns.NONE);
        run.setColor(Colors.forPoi(color, Colors.BODY));
    }

    private int toEmu(double points) {
        return (int) Math.round(points * 12700);
    }

    private ParagraphAlignment alignment(Align align) {
        return switch (align) {
            case CENTER -> ParagraphAlignment.CENTER;
            case RIGHT -> ParagraphAlignment.RIGHT;
            case JUSTIFY -> ParagraphAlignment.BOTH;
            default -> ParagraphAlignment.LEFT;
        };
    }

    private List<Double> columnPercentages(TableBlock block, int columns) {
        List<Double> requested = block.columnWidths();
        double total = 0;
        List<Double> weights = new ArrayList<>();
        if (requested.size() == columns) {
            for (Double value : requested) {
                double weight = value == null || value <= 0 ? 1 : value;
                weights.add(weight);
                total += weight;
            }
        } else {
            for (int i = 0; i < columns; i++) {
                weights.add(1.0);
                total += 1;
            }
        }
        List<Double> percentages = new ArrayList<>();
        for (Double weight : weights) {
            percentages.add(weight * 100.0 / total);
        }
        return percentages;
    }
}
