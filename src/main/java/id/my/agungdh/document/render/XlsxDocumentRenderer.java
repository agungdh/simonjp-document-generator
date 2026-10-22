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
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.FontUnderline;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.IndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.extensions.XSSFCellBorder;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTColor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Excel (.xlsx) rendering. Content flows down a 12-column grid ("Report"
 * sheet); charts are native Excel charts, images are anchored to the grid.
 */
public final class XlsxDocumentRenderer {

    private static final int GRID_COLUMNS = 12;
    private static final int COLUMN_WIDTH_CHARS = 13;
    private static final int MAX_CHUNK = 1600;

    public byte[] render(DocumentRequest request) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024)) {
            new Writer(workbook, request).write();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not render XLSX: " + e.getMessage(), e);
        }
    }

    private record FontKey(String family, double size, boolean bold, boolean italic,
                           boolean underline, String color) {
    }

    private record StyleKey(String family, double size, boolean bold, boolean italic,
                            boolean underline, String color, String background,
                            boolean border, Align align, boolean wrap, boolean middle) {
    }

    private record Segment(String text, TextRun style) {
    }

    private final class Writer {

        private final XSSFWorkbook workbook;
        private final DocumentRequest request;
        private final PageSpec page;
        private final XSSFSheet sheet;
        private final IndexedColorMap indexedColors;
        private final Map<FontKey, XSSFFont> fonts = new HashMap<>();
        private final Map<StyleKey, XSSFCellStyle> styles = new HashMap<>();
        private final ImageLoader imageLoader = new ImageLoader();
        private final OfficeChartWriter chartWriter = new OfficeChartWriter();
        private XSSFDrawing drawing;
        private int row;

        Writer(XSSFWorkbook workbook, DocumentRequest request) {
            this.workbook = workbook;
            this.request = request;
            this.page = PageSpec.of(request);
            this.sheet = workbook.createSheet(sheetName(request.title()));
            this.indexedColors = workbook.getStylesSource().getIndexedColors();
            for (int i = 0; i < GRID_COLUMNS; i++) {
                sheet.setColumnWidth(i, COLUMN_WIDTH_CHARS * 256);
            }
            sheet.setDefaultRowHeightInPoints(15);
            sheet.setFitToPage(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.getPrintSetup().setFitHeight((short) 0);
            sheet.setZoom(90);
        }

        void write() {
            for (DocumentBlock block : request.blocks()) {
                if (block instanceof HeadingBlock heading) {
                    heading(heading);
                } else if (block instanceof ParagraphBlock paragraph) {
                    paragraph(paragraph);
                } else if (block instanceof TableBlock table) {
                    table(table);
                } else if (block instanceof ImageBlock image) {
                    image(image);
                } else if (block instanceof ChartBlock chart) {
                    chart(chart);
                } else if (block instanceof PageBreakBlock) {
                    row += 2;
                }
            }
            var core = workbook.getProperties().getCoreProperties();
            core.setTitle(request.title());
            if (request.author() != null) {
                core.setCreator(request.author());
            }
            if (request.subject() != null) {
                core.setSubjectProperty(request.subject());
            }
        }

        private void heading(HeadingBlock block) {
            String family = Styles.font(block.fontFamily() == null
                    ? Styles.headingFont(block.level()) : block.fontFamily());
            double size = Styles.size(block.fontSize(), Styles.headingSize(block.level()));
            String color = Colors.normalize(block.color(), Colors.PRIMARY);
            Align align = Align.from(block.align());
            XSSFRow sheetRow = row();
            merge(row, 0, GRID_COLUMNS - 1);
            XSSFCell cell = sheetRow.createCell(0);
            cell.setCellStyle(style(family, size, true, false, false, color,
                    null, false, align, true, false));
            cell.setCellValue(block.text());

            int lines = lineCount(block.text(), 100);
            sheetRow.setHeightInPoints((float) Math.min(409,
                    Math.max(size * 1.4 + 6, lines * size * 1.35 + 8 + block.marginAfter())));
            row++;
        }

        private void paragraph(ParagraphBlock block) {
            String family = Styles.font(block.fontFamily());
            double size = Styles.size(block.fontSize(), Styles.BODY_SIZE);
            String color = Colors.normalize(block.color(), Colors.BODY);
            Align align = Align.from(block.align());

            if (block.runs().isEmpty()) {
                XSSFRow sheetRow = row();
                merge(row, 0, GRID_COLUMNS - 1);
                XSSFCell cell = sheetRow.createCell(0);
                cell.setCellStyle(style(family, size, false, false, false, color,
                        null, false, align, true, false));
                cell.setCellValue("");
                sheetRow.setHeightInPoints((float) Math.max(16, block.marginAfter() + 6));
                row++;
                return;
            }

            List<Segment> segments = new ArrayList<>();
            for (TextRun run : block.runs()) {
                String runFamily = Styles.font(run.fontFamily() == null ? family : run.fontFamily());
                double runSize = Styles.size(run.fontSize(), size);
                String runColor = Colors.normalize(run.color(), color);
                TextRun resolved = new TextRun(run.text(), run.bold(), run.italic(), run.underline(),
                        runFamily, runSize, runColor);
                segments.add(new Segment(run.text(), resolved));
            }

            int index = 0;
            while (index < segments.size()) {
                List<Segment> chunk = new ArrayList<>();
                int chars = 0;
                while (index < segments.size() && chars < MAX_CHUNK) {
                    Segment segment = segments.get(index);
                    int capacity = MAX_CHUNK - chars;
                    if (segment.text().length() <= capacity) {
                        chunk.add(segment);
                        chars += segment.text().length();
                        index++;
                    } else {
                        chunk.add(new Segment(segment.text().substring(0, capacity), segment.style()));
                        segments.set(index, new Segment(segment.text().substring(capacity), segment.style()));
                        chars = MAX_CHUNK;
                    }
                }
                if (chunk.isEmpty()) {
                    break;
                }
                writeParagraphChunk(chunk, family, size, color, align, block.marginAfter());
            }
        }

        private void writeParagraphChunk(List<Segment> chunk, String family, double size,
                                         String color, Align align, double marginAfter) {
            XSSFRow sheetRow = row();
            merge(row, 0, GRID_COLUMNS - 1);
            XSSFCell cell = sheetRow.createCell(0);
            cell.setCellStyle(style(family, size, false, false, false, color,
                    null, false, align, true, false));

            StringBuilder text = new StringBuilder();
            for (Segment segment : chunk) {
                text.append(segment.text());
            }
            XSSFRichTextString rich = new XSSFRichTextString(text.toString());
            int offset = 0;
            for (Segment segment : chunk) {
                lenientApplyFont(rich, offset, offset + segment.text().length(), segment.style());
                offset += segment.text().length();
            }
            cell.setCellValue(rich);

            int lines = lineCount(text.toString(), 115);
            double height = Math.max(16, lines * size * 1.35 + 5 + marginAfter);
            sheetRow.setHeightInPoints((float) Math.min(409, height));
            row++;
        }

        private void lenientApplyFont(XSSFRichTextString rich, int start, int end, TextRun style) {
            if (end <= start) {
                return;
            }
            if (style == null) {
                return;
            }
            XSSFFont font = font(style.fontFamily(), style.fontSize() == null ? Styles.BODY_SIZE : style.fontSize(),
                    style.bold(), style.italic(), style.underline(), style.color());
            rich.applyFont(start, end, font);
        }

        private void table(TableBlock block) {
            int columns = block.columnCount();
            if (columns == 0) {
                return;
            }
            double size = Styles.size(block.fontSize(), Styles.TABLE_SIZE);
            String family = Styles.font(block.fontFamily());
            Align align = Align.from(block.align());
            String headerBg = block.headerBackground();
            String headerColor = Colors.normalize(block.headerColor(), "#FFFFFF");
            String stripe = block.stripeColor();
            int[] starts = new int[columns];
            int[] ends = new int[columns];
            for (int c = 0; c < columns; c++) {
                starts[c] = (int) Math.round(c * (double) GRID_COLUMNS / columns);
                ends[c] = (int) Math.round((c + 1) * (double) GRID_COLUMNS / columns) - 1;
                if (ends[c] < starts[c]) {
                    ends[c] = starts[c];
                }
            }

            if (!block.headers().isEmpty()) {
                writeTableRow(block.headers(), columns, starts, ends, family, size, true,
                        headerColor, headerBg, align, 0, true);
            }
            int dataIndex = 0;
            for (List<String> dataRow : block.rows()) {
                String background = dataIndex % 2 == 1 ? stripe : null;
                writeTableRow(dataRow, columns, starts, ends, family, size, false,
                        Colors.BODY, background, align, dataIndex, false);
                dataIndex++;
            }
        }

        private void writeTableRow(List<String> values, int columns, int[] starts, int[] ends,
                                   String family, double size, boolean header, String color,
                                   String background, Align align, int stripeIndex, boolean headerRow) {
            XSSFRow sheetRow = row();
            double maxLines = 1;
            for (int c = 0; c < columns; c++) {
                String value = c < values.size() ? values.get(c) : "";
                int widthInColumns = ends[c] - starts[c] + 1;
                int charsPerLine = Math.max(8, widthInColumns * COLUMN_WIDTH_CHARS);
                maxLines = Math.max(maxLines, lineCount(value, charsPerLine));
                for (int grid = starts[c]; grid <= ends[c]; grid++) {
                    XSSFCell cell = sheetRow.createCell(grid);
                    cell.setCellStyle(style(family, size, header, false, false, color,
                            background, true, align, true, true));
                }
                XSSFCell cell = sheetRow.getCell(starts[c]);
                cell.setCellValue(value);
                if (ends[c] > starts[c]) {
                    merge(row, starts[c], ends[c]);
                }
            }
            sheetRow.setHeightInPoints((float) Math.max(headerRow ? 22 : 18,
                    maxLines * size * 1.35 + 6));
            row++;
        }

        private void image(ImageBlock block) {
            ImageLoader.LoadedImage loaded = imageLoader.load(block.src());
            double gridWidth = gridWidthPoints();
            MediaSizing.Size size = MediaSizing.image(loaded.width(), loaded.height(),
                    block.width(), block.height(), gridWidth, 540);
            int pixelWidth = (int) Math.round(size.width() * 96.0 / 72.0);
            int pixelHeight = (int) Math.round(size.height() * 96.0 / 72.0);
            BufferedImage image = id.my.agungdh.document.support.Images.read(loaded.png());
            byte[] png = id.my.agungdh.document.support.Images.toPng(
                    id.my.agungdh.document.support.Images.scale(image, pixelWidth, pixelHeight));
            int pictureIndex = workbook.addPicture(png, Workbook.PICTURE_TYPE_PNG);
            XSSFClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
            anchor.setCol1(0);
            anchor.setRow1(row);
            anchor.setDx1((int) offsetEmu(block.align(), size.width()));
            XSSFPicture picture = drawing().createPicture(anchor, pictureIndex);
            picture.resize();
            row = picture.getClientAnchor().getRow2() + 1;
            caption(block.caption());
        }

        private void chart(ChartBlock block) {
            if (block.chart() == null) {
                throw new IllegalArgumentException("Chart block is missing the 'chart' object");
            }
            double height = block.height() == null ? 300 : block.height();
            height = Math.min(540, Math.max(120, height));
            int rows = (int) Math.ceil(height / 15.0) + 1;
            if (row + rows > 1_048_000) {
                throw new IllegalArgumentException("Document is too long for an Excel sheet");
            }
            XSSFClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
            anchor.setCol1(0);
            anchor.setRow1(row);
            anchor.setCol2(GRID_COLUMNS);
            anchor.setRow2(row + rows);
            anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
            XSSFChart chart = drawing().createChart(anchor);
            chartWriter.populate(chart, block.chart());
            row += rows + 1;
            caption(block.caption());
        }

        private void caption(String caption) {
            if (caption == null || caption.isBlank()) {
                return;
            }
            XSSFRow sheetRow = row();
            merge(row, 0, GRID_COLUMNS - 1);
            XSSFCell cell = sheetRow.createCell(0);
            cell.setCellStyle(style(Styles.DEFAULT_FONT, Styles.CAPTION_SIZE, false, true,
                    false, Colors.CAPTION, null, false, Align.CENTER, true, false));
            cell.setCellValue(caption);
            sheetRow.setHeightInPoints(16);
            row++;
        }

        private XSSFRow row() {
            XSSFRow sheetRow = sheet.getRow(row);
            return sheetRow == null ? sheet.createRow(row) : sheetRow;
        }

        private void merge(int rowIndex, int firstColumn, int lastColumn) {
            sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, firstColumn, lastColumn));
        }

        private XSSFDrawing drawing() {
            if (drawing == null) {
                drawing = sheet.createDrawingPatriarch();
            }
            return drawing;
        }

        private long offsetEmu(String align, double widthPoints) {
            double widthPixels = widthPoints * 96.0 / 72.0;
            double free = Math.max(0, gridWidthPixels() - widthPixels);
            double pixels = switch (Align.from(align)) {
                case CENTER -> free / 2.0;
                case RIGHT -> free;
                default -> 0;
            };
            return Math.round(pixels * 9525);
        }

        private double gridWidthPixels() {
            double total = 0;
            for (int i = 0; i < GRID_COLUMNS; i++) {
                total += sheet.getColumnWidthInPixels(i);
            }
            return total;
        }

        private double gridWidthPoints() {
            return gridWidthPixels() * 72.0 / 96.0;
        }

        private XSSFFont font(String family, double size, boolean bold, boolean italic,
                              boolean underline, String color) {
            FontKey key = new FontKey(family, size, bold, italic, underline, color);
            return fonts.computeIfAbsent(key, k -> {
                XSSFFont font = workbook.createFont();
                font.setFontName(Styles.font(k.family()));
                font.setFontHeightInPoints((short) Math.round(k.size()));
                font.setBold(k.bold());
                font.setItalic(k.italic());
                font.setUnderline(k.underline() ? FontUnderline.SINGLE : FontUnderline.NONE);
                font.setColor(xssfColor(k.color(), Colors.BODY));
                return font;
            });
        }

        private XSSFCellStyle style(String family, double size, boolean bold, boolean italic,
                                    boolean underline, String color, String background,
                                    boolean border, Align align, boolean wrap, boolean middle) {
            StyleKey key = new StyleKey(family, size, bold, italic, underline, color,
                    background, border, align, wrap, middle);
            return styles.computeIfAbsent(key, k -> {
                XSSFCellStyle style = workbook.createCellStyle();
                style.setFont(font(k.family(), k.size(), k.bold(), k.italic(), k.underline(), k.color()));
                style.setWrapText(k.wrap());
                style.setVerticalAlignment(k.middle() ? VerticalAlignment.CENTER : VerticalAlignment.TOP);
                style.setAlignment(horizontal(k.align()));
                if (k.background() != null) {
                    style.setFillForegroundColor(xssfColor(k.background(), "#FFFFFF"));
                    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                }
                if (k.border()) {
                    XSSFColor borderColor = xssfColor(Colors.BORDER, "#BFBFBF");
                    style.setBorderTop(BorderStyle.THIN);
                    style.setBorderBottom(BorderStyle.THIN);
                    style.setBorderLeft(BorderStyle.THIN);
                    style.setBorderRight(BorderStyle.THIN);
                    style.setBorderColor(XSSFCellBorder.BorderSide.TOP, borderColor);
                    style.setBorderColor(XSSFCellBorder.BorderSide.BOTTOM, borderColor);
                    style.setBorderColor(XSSFCellBorder.BorderSide.LEFT, borderColor);
                    style.setBorderColor(XSSFCellBorder.BorderSide.RIGHT, borderColor);
                }
                return style;
            });
        }

        private HorizontalAlignment horizontal(Align align) {
            return switch (align) {
                case CENTER -> HorizontalAlignment.CENTER;
                case RIGHT -> HorizontalAlignment.RIGHT;
                case JUSTIFY -> HorizontalAlignment.JUSTIFY;
                default -> HorizontalAlignment.LEFT;
            };
        }

        private XSSFColor xssfColor(String value, String fallback) {
            String hex = Colors.normalize(value, fallback);
            CTColor color = CTColor.Factory.newInstance();
            color.setRgb(new byte[]{
                    (byte) 0xFF,
                    (byte) Integer.parseInt(hex.substring(1, 3), 16),
                    (byte) Integer.parseInt(hex.substring(3, 5), 16),
                    (byte) Integer.parseInt(hex.substring(5, 7), 16)
            });
            return XSSFColor.from(color, indexedColors);
        }

        private int lineCount(String text, int charactersPerLine) {
            if (text == null || text.isEmpty()) {
                return 1;
            }
            int lines = 0;
            for (String part : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
                lines += Math.max(1, (int) Math.ceil(part.length() / (double) charactersPerLine));
            }
            return Math.max(1, lines);
        }
    }

    private String sheetName(String title) {
        String name = title == null ? "Report" : title.replaceAll("[\\\\/*?\\[\\]:]", " ").trim();
        if (name.isBlank()) {
            name = "Report";
        }
        return name.length() > 31 ? name.substring(0, 31) : name;
    }
}
