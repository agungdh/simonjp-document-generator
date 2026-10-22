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
import id.my.agungdh.document.support.Styles;
import org.apache.poi.ooxml.POIXMLDocumentPart;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.sl.usermodel.TableCell;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.TextShape;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFAutoShape;
import org.apache.poi.xslf.usermodel.XSLFChart;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.presentationml.x2006.main.CTGraphicalObjectFrame;
import org.openxmlformats.schemas.presentationml.x2006.main.CTGroupShape;

import javax.xml.namespace.QName;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PowerPoint (.pptx) rendering. The document flows onto 16:9 slides; headings,
 * paragraphs, tables and media break to a new slide when space runs out.
 * Charts are native, editable PowerPoint charts.
 */
public final class PptxDocumentRenderer {

    private static final String CHART_URI = "http://schemas.openxmlformats.org/drawingml/2006/chart";
    private static final String REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final double SLIDE_WIDTH = 960;
    private static final double SLIDE_HEIGHT = 540;
    private static final double MARGIN = 64;
    private static final double CONTENT_WIDTH = SLIDE_WIDTH - MARGIN * 2;
    private static final double CONTENT_TOP = 54;
    private static final double CONTENT_BOTTOM = 486;
    private static final double BODY_SIZE = 16;

    private final ImageLoader imageLoader = new ImageLoader();
    private final OfficeChartWriter chartWriter = new OfficeChartWriter();

    public byte[] render(DocumentRequest request) {
        try (XMLSlideShow ppt = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024)) {
            ppt.setPageSize(new Dimension((int) SLIDE_WIDTH, (int) SLIDE_HEIGHT));
            var core = ppt.getProperties().getCoreProperties();
            core.setTitle(request.title());
            if (request.author() != null) {
                core.setCreator(request.author());
            }

            titleSlide(ppt, request);
            Writer writer = new Writer(ppt);
            for (DocumentBlock block : request.blocks()) {
                if (block instanceof HeadingBlock heading) {
                    writer.heading(heading);
                } else if (block instanceof ParagraphBlock paragraph) {
                    writer.paragraph(paragraph);
                } else if (block instanceof TableBlock table) {
                    writer.table(table);
                } else if (block instanceof ImageBlock image) {
                    writer.image(image);
                } else if (block instanceof ChartBlock chart) {
                    writer.chart(chart);
                } else if (block instanceof PageBreakBlock) {
                    writer.newContentSlide();
                }
            }
            writer.addFooters(request);
            ppt.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not render PPTX: " + e.getMessage(), e);
        }
    }

    private void titleSlide(XMLSlideShow ppt, DocumentRequest request) {
        XSLFSlide slide = ppt.createSlide();

        XSLFAutoShape panel = slide.createAutoShape();
        panel.setShapeType(ShapeType.RECT);
        panel.setAnchor(new Rectangle2D.Double(760, 0, 200, SLIDE_HEIGHT));
        panel.setFillColor(new Color(0xEA, 0xF1, 0xF8));
        panel.setLineColor(null);

        XSLFAutoShape accent = slide.createAutoShape();
        accent.setShapeType(ShapeType.RECT);
        accent.setAnchor(new Rectangle2D.Double(MARGIN, 172, 72, 7));
        accent.setFillColor(new Color(0x2E, 0x75, 0xB6));
        accent.setLineColor(null);

        textBox(slide, MARGIN, 200, 640, 150,
                List.of(new TextRun(request.title(), true, false, false,
                        Styles.DEFAULT_FONT, 38.0, "#1F4E79")),
                Align.LEFT, 0);
        if (request.subtitle() != null && !request.subtitle().isBlank()) {
            textBox(slide, MARGIN, 360, 640, 70,
                    List.of(new TextRun(request.subtitle(), false, false, false,
                            Styles.DEFAULT_FONT, 19.0, "#555555")),
                    Align.LEFT, 0);
        }
        StringBuilder meta = new StringBuilder();
        if (request.author() != null && !request.author().isBlank()) {
            meta.append(request.author());
        }
        if (meta.length() > 0) {
            meta.append("   |   ");
        }
        meta.append(LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)));
        textBox(slide, MARGIN, 462, 640, 30,
                List.of(new TextRun(meta.toString(), false, false, false,
                        Styles.DEFAULT_FONT, 12.0, "#777777")),
                Align.LEFT, 0);
    }

    private final class Writer {

        private final XMLSlideShow ppt;
        private XSLFSlide slide;
        private double y;
        private int mediaIndex;

        Writer(XMLSlideShow ppt) {
            this.ppt = ppt;
        }

        void heading(HeadingBlock block) {
            double size = Styles.size(block.fontSize(), switch (block.level()) {
                case 1 -> 30;
                case 2 -> 24;
                case 3 -> 19;
                default -> 16;
            });
            String family = Styles.font(block.fontFamily() == null
                    ? Styles.headingFont(block.level()) : block.fontFamily());
            String color = Colors.normalize(block.color(), Colors.PRIMARY);
            double height = size * 1.35 + block.marginAfter() + 8;
            ensure(height);
            textBox(slide, MARGIN, y, CONTENT_WIDTH, height,
                    List.of(new TextRun(block.text(), true, false, false, family, size, color)),
                    Align.from(block.align()), block.marginAfter());
            y += height;
        }

        void paragraph(ParagraphBlock block) {
            String family = Styles.font(block.fontFamily());
            double size = Styles.size(block.fontSize(), BODY_SIZE);
            String color = Colors.normalize(block.color(), Colors.BODY);
            List<TextRun> runs = new ArrayList<>();
            if (block.runs().isEmpty()) {
                runs.add(new TextRun("", false, false, false, family, size, color));
            } else {
                for (TextRun run : block.runs()) {
                    runs.add(new TextRun(run.text(), run.bold(), run.italic(), run.underline(),
                            run.fontFamily() == null ? family : run.fontFamily(),
                            Styles.size(run.fontSize(), size),
                            Colors.normalize(run.color(), color)));
                }
            }
            double maxSize = runs.stream().mapToDouble(r -> r.fontSize() == null ? size : r.fontSize())
                    .max().orElse(size);
            double height = estimatedHeight(runs, CONTENT_WIDTH, maxSize) + block.marginAfter();
            ensure(height);
            textBox(slide, MARGIN, y, CONTENT_WIDTH, height, runs, Align.from(block.align()), block.marginAfter());
            y += height;
        }

        void table(TableBlock block) {
            int columns = block.columnCount();
            if (columns == 0) {
                return;
            }
            double size = Styles.size(block.fontSize(), 11.0);
            String family = Styles.font(block.fontFamily());
            String headerBg = Colors.normalize(block.headerBackground(), Colors.PRIMARY);
            String headerColor = Colors.normalize(block.headerColor(), "#FFFFFF");
            String stripe = Colors.normalize(block.stripeColor(), "#F2F2F2");
            boolean hasHeader = !block.headers().isEmpty();
            Align align = Align.from(block.align());

            List<List<String>> rows = block.rows();
            int index = 0;
            boolean firstChunk = true;
            while (index < rows.size() || (firstChunk && rows.isEmpty() && hasHeader)) {
                double available = CONTENT_BOTTOM - Math.max(y, CONTENT_TOP);
                double headerHeight = hasHeader ? 30 : 0;
                int capacity = Math.max(1, (int) Math.floor((available - headerHeight - 6) / rowHeight(size, block, columns)));
                int take = Math.min(capacity, Math.max(0, rows.size() - index));
                if (rows.isEmpty() && hasHeader) {
                    take = 0;
                }
                if (y + headerHeight + take * rowHeight(size, block, columns) > CONTENT_BOTTOM) {
                    newContentSlide();
                }
                y = Math.max(y, CONTENT_TOP);

                XSLFTable table = slide.createTable();
                double tableHeight = headerHeight + take * rowHeight(size, block, columns);
                table.setAnchor(new Rectangle2D.Double(MARGIN, y, CONTENT_WIDTH, tableHeight));

                if (hasHeader) {
                    XSLFTableRow header = table.addRow();
                    header.setHeight(30);
                    for (int c = 0; c < columns; c++) {
                        String value = c < block.headers().size() ? block.headers().get(c) : "";
                        XSLFTableCell cell = header.addCell();
                        styleCell(cell, value, family, size, true, headerColor, headerBg, align);
                    }
                }
                for (int r = 0; r < take; r++) {
                    List<String> data = rows.get(index + r);
                    XSLFTableRow tableRow = table.addRow();
                    tableRow.setHeight(rowHeight(size, block, columns));
                    for (int c = 0; c < columns; c++) {
                        String value = c < data.size() ? data.get(c) : "";
                        XSLFTableCell cell = tableRow.addCell();
                        String background = (index + r) % 2 == 1 ? stripe : "#FFFFFF";
                        styleCell(cell, value, family, size, false, Colors.BODY, background, align);
                    }
                }
                for (int c = 0; c < columns; c++) {
                    table.setColumnWidth(c, CONTENT_WIDTH / columns);
                }
                index += take;
                y += tableHeight + 14;
                firstChunk = false;
                if (index < rows.size()) {
                    newContentSlide();
                }
            }
        }

        void image(ImageBlock block) {
            ImageLoader.LoadedImage loaded = imageLoader.load(block.src());
            MediaSizing.Size size = MediaSizing.image(loaded.width(), loaded.height(),
                    block.width(), block.height(), CONTENT_WIDTH, CONTENT_BOTTOM - CONTENT_TOP - 30);
            if (y + size.height() + 20 > CONTENT_BOTTOM) {
                newContentSlide();
            }
            XSLFPictureData data = ppt.addPicture(loaded.png(), PictureData.PictureType.PNG);
            XSLFPictureShape picture = slide.createPicture(data);
            double x = switch (Align.from(block.align())) {
                case RIGHT -> MARGIN + CONTENT_WIDTH - size.width();
                case CENTER -> MARGIN + (CONTENT_WIDTH - size.width()) / 2;
                default -> MARGIN;
            };
            picture.setAnchor(new Rectangle2D.Double(x, y, size.width(), size.height()));
            y += size.height() + 6;
            caption(block.caption());
        }

        void chart(ChartBlock block) {
            if (block.chart() == null) {
                throw new IllegalArgumentException("Chart block is missing the 'chart' object");
            }
            double height = Math.min(420, Math.max(200, block.height() == null ? 330 : block.height()));
            if (y + height + 20 > CONTENT_BOTTOM) {
                newContentSlide();
            }
            double width = Math.min(CONTENT_WIDTH, Math.max(380, block.width() == null ? CONTENT_WIDTH : block.width()));
            double x = MARGIN + Math.max(0, (CONTENT_WIDTH - width) / 2);
            XSLFChart chart = ppt.createChart(slide);
            chartWriter.populate(chart, block.chart());
            addChartFrame(slide, chart, new Rectangle2D.Double(x, y, width, height));
            y += height + 6;
            caption(block.caption());
        }

        void newContentSlide() {
            slide = ppt.createSlide();
            y = CONTENT_TOP;
            XSLFAutoShape line = slide.createAutoShape();
            line.setShapeType(ShapeType.RECT);
            line.setAnchor(new Rectangle2D.Double(0, 0, SLIDE_WIDTH, 5));
            line.setFillColor(new Color(0x2E, 0x75, 0xB6));
            line.setLineColor(null);
        }

        void addFooters(DocumentRequest request) {
            List<XSLFSlide> slides = ppt.getSlides();
            String title = request.title();
            for (int i = 1; i < slides.size(); i++) {
                XSLFSlide current = slides.get(i);
                XSLFAutoShape line = current.createAutoShape();
                line.setShapeType(ShapeType.RECT);
                line.setAnchor(new Rectangle2D.Double(MARGIN, 500, CONTENT_WIDTH, 0.8));
                line.setFillColor(new Color(0xD9, 0xD9, 0xD9));
                line.setLineColor(null);

                textBox(current, MARGIN, 507, CONTENT_WIDTH - 70, 18,
                        List.of(new TextRun(title, false, false, false,
                                Styles.DEFAULT_FONT, 9.0, "#888888")),
                        Align.LEFT, 0);
                textBox(current, SLIDE_WIDTH - MARGIN - 60, 507, 60, 18,
                        List.of(new TextRun((i + 1) + " / " + slides.size(), false, false, false,
                                Styles.DEFAULT_FONT, 9.0, "#888888")),
                        Align.RIGHT, 0);
            }
        }

        private void caption(String caption) {
            if (caption == null || caption.isBlank()) {
                return;
            }
            ensure(22);
            textBox(slide, MARGIN, y, CONTENT_WIDTH, 20,
                    List.of(new TextRun(caption, false, true, false,
                            Styles.DEFAULT_FONT, 9.5, Colors.CAPTION)),
                    Align.CENTER, 0);
            y += 20;
        }

        private void ensure(double height) {
            if (slide == null || y + height > CONTENT_BOTTOM) {
                newContentSlide();
            }
        }

        private void styleCell(XSLFTableCell cell, String value, String family, double size,
                               boolean bold, String color, String background, Align align) {
            cell.setFillColor(Colors.parse(background, Color.WHITE));
            cell.setInsets(new org.apache.poi.sl.usermodel.Insets2D(4, 4, 4, 4));
            cell.setVerticalAlignment(VerticalAlignment.MIDDLE);
            for (TableCell.BorderEdge edge : TableCell.BorderEdge.values()) {
                cell.setBorderWidth(edge, 0.75);
                cell.setBorderColor(edge, new Color(0xBF, 0xBF, 0xBF));
            }
            cell.clearText();
            XSLFTextParagraph paragraph = cell.getTextParagraphs().isEmpty()
                    ? cell.addNewTextParagraph() : cell.getTextParagraphs().getFirst();
            paragraph.setTextAlign(textAlign(align));
            paragraph.setSpaceAfter(0d);
            paragraph.setSpaceBefore(0d);
            addRun(paragraph, value, family, size, bold, false, false, color);
        }

        private void addChartFrame(XSLFSlide target, XSLFChart chart, Rectangle2D anchor) {
            String relationId = null;
            for (POIXMLDocumentPart.RelationPart part : target.getRelationParts()) {
                if (part.getDocumentPart() == chart) {
                    relationId = part.getRelationship().getId();
                    break;
                }
            }
            if (relationId == null) {
                throw new IllegalStateException("Could not link the PowerPoint chart to its slide");
            }

            CTGroupShape tree = target.getXmlObject().getCSld().getSpTree();
            int shapeId = target.getShapes().stream().mapToInt(XSLFShape::getShapeId).max().orElse(1) + 1;
            CTGraphicalObjectFrame frame = CTGraphicalObjectFrame.Factory.newInstance();
            var nonVisual = frame.addNewNvGraphicFramePr();
            var properties = nonVisual.addNewCNvPr();
            properties.setName("Chart " + shapeId);
            properties.setId(shapeId);
            nonVisual.addNewCNvGraphicFramePr().addNewGraphicFrameLocks().setNoGrp(true);
            nonVisual.addNewNvPr();

            var transform = frame.addNewXfrm();
            var offset = transform.addNewOff();
            offset.setX(Math.round(anchor.getX() * 12700));
            offset.setY(Math.round(anchor.getY() * 12700));
            var extent = transform.addNewExt();
            extent.setCx(Math.round(anchor.getWidth() * 12700));
            extent.setCy(Math.round(anchor.getHeight() * 12700));

            var data = frame.addNewGraphic().addNewGraphicData();
            try (XmlCursor cursor = data.newCursor()) {
                cursor.toNextToken();
                cursor.beginElement(new QName(CHART_URI, "chart"));
                cursor.insertAttributeWithValue("id", REL_NS, relationId);
            }
            data.setUri(CHART_URI);

            int index = tree.sizeOfGraphicFrameArray();
            tree.addNewGraphicFrame();
            tree.setGraphicFrameArray(index, frame);
        }
    }

    private void textBox(XSLFSlide slide, double x, double y, double width, double height,
                         List<TextRun> runs, Align align, double marginAfter) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle2D.Double(x, y, width, height));
        box.setWordWrap(true);
        box.setTextAutofit(TextShape.TextAutofit.NORMAL);
        box.setInsets(new org.apache.poi.sl.usermodel.Insets2D(0, 0, 0, 0));
        box.clearText();

        XSLFTextParagraph paragraph = box.addNewTextParagraph();
        paragraph.setTextAlign(textAlign(align));
        paragraph.setSpaceAfter(marginAfter);
        paragraph.setSpaceBefore(0d);
        for (TextRun run : runs) {
            addRun(paragraph, run.text(), Styles.font(run.fontFamily()),
                    Styles.size(run.fontSize(), BODY_SIZE), run.bold(), run.italic(),
                    run.underline(), Colors.normalize(run.color(), Colors.BODY));
        }
    }

    private void addRun(XSLFTextParagraph paragraph, String text, String family, double size,
                        boolean bold, boolean italic, boolean underline, String color) {
        String[] lines = (text == null ? "" : text).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                paragraph.addLineBreak();
            }
            if (lines[i].isEmpty() && i > 0) {
                continue;
            }
            XSLFTextRun run = paragraph.addNewTextRun();
            run.setText(lines[i]);
            run.setFontFamily(Styles.font(family));
            run.setFontSize(size);
            run.setBold(bold);
            run.setItalic(italic);
            run.setUnderlined(underline);
            run.setFontColor(Colors.parse(color, Colors.parse(Colors.BODY)));
        }
    }

    private double estimatedHeight(List<TextRun> runs, double width, double size) {
        int characters = 0;
        int newlines = 0;
        for (TextRun run : runs) {
            characters += run.text().length();
            for (int i = 0; i < run.text().length(); i++) {
                if (run.text().charAt(i) == '\n') {
                    newlines++;
                }
            }
        }
        double charsPerLine = Math.max(12, width / (size * 0.55));
        int lines = Math.max(1, (int) Math.ceil(characters / charsPerLine) + newlines);
        return lines * size * 1.3 + 4;
    }

    private double rowHeight(double size, TableBlock block, int columns) {
        int maxChars = 0;
        for (List<String> row : block.rows()) {
            for (String value : row) {
                maxChars = Math.max(maxChars, value == null ? 0 : value.length());
            }
        }
        double cellWidth = CONTENT_WIDTH / columns;
        double charsPerLine = Math.max(8, cellWidth / (size * 0.55));
        int lines = Math.max(1, (int) Math.ceil(maxChars / charsPerLine));
        return Math.max(24, Math.min(90, lines * size * 1.3 + 8));
    }

    private static TextParagraph.TextAlign textAlign(Align align) {
        return switch (align) {
            case CENTER -> TextParagraph.TextAlign.CENTER;
            case RIGHT -> TextParagraph.TextAlign.RIGHT;
            case JUSTIFY -> TextParagraph.TextAlign.JUSTIFY;
            default -> TextParagraph.TextAlign.LEFT;
        };
    }
}
