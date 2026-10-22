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
import id.my.agungdh.document.support.PageSpec;
import id.my.agungdh.document.support.PdfChartDrawer;
import id.my.agungdh.document.support.PdfFonts;
import id.my.agungdh.document.support.Styles;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF rendering with Apache PDFBox. Everything is drawn directly: rich text is
 * wrapped and positioned by this renderer, tables and images are painted with
 * PDF operators, and charts are emitted as vectors. No HTML, Office file, or
 * intermediate image is involved.
 */
public final class PdfDocumentRenderer {

    private static final float CELL_PADDING_X = 5;
    private static final float CELL_PADDING_Y = 4;

    private final ImageLoader imageLoader = new ImageLoader();

    public byte[] render(DocumentRequest request) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024)) {
            new Layout(document, request).write();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not render PDF: " + e.getMessage(), e);
        }
    }

    private record Style(PDFont font, float size, Color color, boolean underline) {
    }

    private record Token(String text, Style style, boolean space, float width, boolean newline) {
    }

    private record Line(List<Token> tokens, boolean hardBreak) {
    }

    private final class Layout {

        private final PDDocument document;
        private final DocumentRequest request;
        private final PageSpec page;
        private final PdfFonts fonts;
        private final PdfChartDrawer charts;
        private final List<PDPage> pages = new ArrayList<>();
        private PDPageContentStream content;
        private float top;

        Layout(PDDocument document, DocumentRequest request) {
            this.document = document;
            this.request = request;
            this.page = PageSpec.of(request);
            this.fonts = new PdfFonts(document);
            this.charts = new PdfChartDrawer(fonts);
        }

        void write() throws IOException {
            newPage();
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
                    newPage();
                }
            }
            content.close();
            content = null;
            pageFooters();
            metadata();
        }

        private void newPage() throws IOException {
            if (content != null) {
                content.close();
            }
            PDPage pdPage = new PDPage(new PDRectangle((float) page.width(), (float) page.height()));
            document.addPage(pdPage);
            pages.add(pdPage);
            content = new PDPageContentStream(document, pdPage);
            top = (float) page.height() - (float) page.margin();
        }

        private void ensure(float height) throws IOException {
            if (top - height < page.margin() && top < page.height() - page.margin() - 1) {
                newPage();
            }
        }

        private void heading(HeadingBlock block) throws IOException {
            String family = block.fontFamily() == null ? Styles.headingFont(block.level()) : block.fontFamily();
            double size = Styles.size(block.fontSize(), Styles.headingSize(block.level()));
            Color color = Colors.parse(Colors.normalize(block.color(), Colors.PRIMARY));
            Style style = new Style(fonts.font(family, true, false), (float) size, color, false);
            List<Line> lines = layout(List.of(new TextRun(block.text(), true, false, false,
                            family, size, Colors.toHex(color))),
                    family, size, color, contentWidth());
            if (!lines.isEmpty()) {
                float firstHeight = lineHeight(lines.getFirst(), (float) size, 1.2f);
                ensure(Math.min(totalHeight(lines, (float) size, 1.2f), firstHeight * 2));
            }
            drawLines(lines, Align.from(block.align()), (float) size, 1.2f, 0);
            top -= (float) (double) block.marginAfter();
        }

        private void paragraph(ParagraphBlock block) throws IOException {
            String family = Styles.font(block.fontFamily());
            double size = Styles.size(block.fontSize(), Styles.BODY_SIZE);
            Color color = Colors.parse(Colors.normalize(block.color(), Colors.BODY));
            List<TextRun> runs = block.runs().isEmpty()
                    ? List.of(new TextRun(" ", false, false, false, null, null, null))
                    : block.runs();
            List<Line> lines = layout(runs, family, size, color, contentWidth());
            drawLines(lines, Align.from(block.align()), (float) size, (float) (double) block.lineSpacing(),
                    (float) (double) block.marginAfter());
        }

        /**
         * Turns styled runs into wrapped lines. Spaces stay part of the line so
         * justification can distribute the remaining width.
         */
        private List<Line> layout(List<TextRun> runs, String baseFamily, double baseSize,
                                  Color baseColor, float maxWidth) {
            List<Token> tokens = new ArrayList<>();
            for (TextRun run : runs) {
                String family = run.fontFamily() == null ? baseFamily : run.fontFamily();
                double size = Styles.size(run.fontSize(), baseSize);
                boolean bold = Boolean.TRUE.equals(run.bold());
                boolean italic = Boolean.TRUE.equals(run.italic());
                Color color = Colors.parse(Colors.normalize(run.color(), Colors.toHex(baseColor)));
                Style style = new Style(fonts.font(family, bold, italic), (float) size, color,
                        Boolean.TRUE.equals(run.underline()));
                addTokens(tokens, run.text() == null ? "" : run.text(), style);
            }

            List<Line> lines = new ArrayList<>();
            List<Token> line = new ArrayList<>();
            float width = 0;
            for (Token token : tokens) {
                if (token.newline()) {
                    width -= trimTrailingSpaces(line);
                    lines.add(new Line(List.copyOf(line), true));
                    line.clear();
                    width = 0;
                    continue;
                }
                if (token.space()) {
                    if (!line.isEmpty()) {
                        line.add(token);
                        width += token.width();
                    }
                    continue;
                }
                if (width + token.width() > maxWidth && !line.isEmpty()) {
                    width -= trimTrailingSpaces(line);
                    lines.add(new Line(List.copyOf(line), false));
                    line.clear();
                    width = 0;
                }
                if (token.width() > maxWidth && line.isEmpty()) {
                    width = addSplitToken(line, lines, token, maxWidth, width);
                } else {
                    line.add(token);
                    width += token.width();
                }
            }
            if (!line.isEmpty()) {
                trimTrailingSpaces(line);
                lines.add(new Line(List.copyOf(line), false));
            }
            if (lines.isEmpty()) {
                lines.add(new Line(List.of(), false));
            }
            return lines;
        }

        private void addTokens(List<Token> tokens, String text, Style style) {
            String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
            String[] parts = normalized.split("\n", -1);
            for (int p = 0; p < parts.length; p++) {
                if (p > 0) {
                    tokens.add(new Token("", style, false, 0, true));
                }
                String part = parts[p];
                int i = 0;
                while (i < part.length()) {
                    boolean space = Character.isWhitespace(part.charAt(i));
                    int start = i;
                    while (i < part.length() && Character.isWhitespace(part.charAt(i)) == space) {
                        i++;
                    }
                    String value = part.substring(start, i).replace('\t', ' ');
                    if (!value.isEmpty()) {
                        tokens.add(new Token(value, style, space, fonts.width(style.font(), style.size(), value), false));
                    }
                }
            }
        }

        /**
         * Splits one over-long word across lines. Returns the width still used
         * on the current line.
         */
        private float addSplitToken(List<Token> line, List<Line> lines, Token token,
                                    float maxWidth, float width) {
            String remaining = token.text();
            while (!remaining.isEmpty()) {
                int end = remaining.length();
                while (end > 0 && fonts.width(token.style().font(), token.style().size(),
                        remaining.substring(0, end)) > maxWidth) {
                    end--;
                }
                if (end == 0) {
                    end = 1;
                }
                String part = remaining.substring(0, end);
                remaining = remaining.substring(end);
                line.add(new Token(part, token.style(), false,
                        fonts.width(token.style().font(), token.style().size(), part), false));
                if (!remaining.isEmpty()) {
                    lines.add(new Line(List.copyOf(line), false));
                    line.clear();
                }
            }
            for (Token value : line) {
                width += value.width();
            }
            return width;
        }

        private float trimTrailingSpaces(List<Token> line) {
            float removed = 0;
            while (!line.isEmpty() && line.getLast().space()) {
                removed += line.removeLast().width();
            }
            return removed;
        }

        private void drawLines(List<Line> lines, Align align, float baseSize, float lineSpacing,
                               float marginAfter) throws IOException {
            for (int i = 0; i < lines.size(); i++) {
                Line line = lines.get(i);
                float lineHeight = lineHeight(line, baseSize, lineSpacing);
                ensure(lineHeight);
                PDFont baseFont = fonts.font(Styles.DEFAULT_FONT, false, false);
                float maxSize = baseSize;
                PDFont maxFont = baseFont;
                for (Token token : line.tokens()) {
                    if (token.style().size() > maxSize) {
                        maxSize = token.style().size();
                        maxFont = token.style().font();
                    }
                }
                float asc = fonts.ascent(maxFont, maxSize);
                float desc = fonts.descent(maxFont, maxSize);
                float baseline = top - asc - Math.max(0, (lineHeight - asc - desc) / 2);
                float natural = 0;
                int gaps = 0;
                for (Token token : line.tokens()) {
                    natural += token.width();
                    if (token.space()) {
                        gaps++;
                    }
                }
                float extra = 0;
                if (align == Align.JUSTIFY && i < lines.size() - 1 && !line.hardBreak() && gaps > 0) {
                    extra = Math.max(0, (contentWidth() - natural) / gaps);
                }
                float x = switch (align) {
                    case CENTER -> (float) page.margin() + (contentWidth() - natural) / 2f;
                    case RIGHT -> (float) page.margin() + contentWidth() - natural;
                    default -> (float) page.margin();
                };
                for (Token token : line.tokens()) {
                    if (!token.space() && !token.text().isEmpty()) {
                        text(token.style().font(), token.style().size(), x, baseline,
                                token.text(), token.style().color());
                        if (token.style().underline()) {
                            float thickness = Math.max(0.5f, token.style().size() * 0.055f);
                            content.setStrokingColor(token.style().color());
                            content.setLineWidth(thickness);
                            content.moveTo(x, baseline - token.style().size() * 0.12f);
                            content.lineTo(x + token.width(), baseline - token.style().size() * 0.12f);
                            content.stroke();
                        }
                    }
                    x += token.width() + (token.space() ? extra : 0);
                }
                top -= lineHeight;
            }
            top -= marginAfter;
        }

        private float totalHeight(List<Line> lines, float baseSize, float lineSpacing) {
            float total = 0;
            for (Line line : lines) {
                total += lineHeight(line, baseSize, lineSpacing);
            }
            return total;
        }

        private float lineHeight(Line line, float baseSize, float lineSpacing) {
            float maxSize = baseSize;
            for (Token token : line.tokens()) {
                maxSize = Math.max(maxSize, token.style().size());
            }
            return maxSize * 1.25f * lineSpacing;
        }

        private void table(TableBlock block) throws IOException {
            int columns = block.columnCount();
            if (columns == 0) {
                return;
            }
            String family = Styles.font(block.fontFamily());
            float size = (float) Styles.size(block.fontSize(), Styles.TABLE_SIZE);
            PDFont regular = fonts.font(family, false, false);
            PDFont bold = fonts.font(family, true, false);
            float lineStep = size * 1.3f;
            float[] widths = columnWidths(block, columns);
            float[] starts = new float[columns + 1];
            starts[0] = (float) page.margin();
            for (int c = 0; c < columns; c++) {
                starts[c + 1] = starts[c] + widths[c];
            }

            List<List<String>> headerLines = null;
            Color border = Colors.parse(Colors.normalize(block.borderColor(), Colors.BORDER));
            if (!block.headers().isEmpty()) {
                headerLines = wrapRow(block.headers(), columns, widths, bold, size);
            }
            float headerHeight = headerLines == null ? 0 : rowHeight(headerLines, lineStep);
            if (headerHeight > 0) {
                ensure(headerHeight + Math.min(28, contentHeight() / 3));
                drawTableRow(headerLines, starts, widths, headerHeight, lineStep, bold, size,
                        Align.from(block.align()), Colors.parse(Colors.normalize(block.headerBackground(),
                                Colors.PRIMARY)), Colors.parse(Colors.normalize(block.headerColor(), "#FFFFFF")),
                        border);
                top -= headerHeight;
            }

            Color stripe = Colors.parse(Colors.normalize(block.stripeColor(), "#F2F2F2"));
            int index = 0;
            for (List<String> row : block.rows()) {
                List<List<String>> cellLines = wrapRow(row, columns, widths, regular, size);
                float height = rowHeight(cellLines, lineStep);
                Color background = index % 2 == 1 ? stripe : Color.WHITE;
                if (top - height >= page.margin()) {
                    drawTableRow(cellLines, starts, widths, height, lineStep, regular, size,
                            Align.from(block.align()), background, Colors.parse(Colors.BODY), border);
                    top -= height;
                } else if (height <= contentHeight() - headerHeight - 8) {
                    newPage();
                    if (headerLines != null) {
                        drawTableRow(headerLines, starts, widths, headerHeight, lineStep, bold, size,
                                Align.from(block.align()), Colors.parse(Colors.normalize(
                                        block.headerBackground(), Colors.PRIMARY)),
                                Colors.parse(Colors.normalize(block.headerColor(), "#FFFFFF")), border);
                        top -= headerHeight;
                    }
                    drawTableRow(cellLines, starts, widths, height, lineStep, regular, size,
                            Align.from(block.align()), background, Colors.parse(Colors.BODY), border);
                    top -= height;
                } else {
                    newPage();
                    if (headerLines != null) {
                        drawTableRow(headerLines, starts, widths, headerHeight, lineStep, bold, size,
                                Align.from(block.align()), Colors.parse(Colors.normalize(
                                        block.headerBackground(), Colors.PRIMARY)),
                                Colors.parse(Colors.normalize(block.headerColor(), "#FFFFFF")), border);
                        top -= headerHeight;
                    }
                    int maxLines = Math.max(1, (int) Math.floor(
                            (top - page.margin() - 8) / lineStep));
                    while (!empty(cellLines)) {
                        List<List<String>> chunk = new ArrayList<>();
                        int used = 0;
                        for (List<String> lines : cellLines) {
                            int take = Math.min(maxLines, lines.size());
                            chunk.add(new ArrayList<>(lines.subList(0, take)));
                            used = Math.max(used, take);
                            if (take == lines.size()) {
                                lines.clear();
                            } else {
                                lines.subList(0, take).clear();
                            }
                        }
                        float chunkHeight = used * lineStep + 8;
                        drawTableRow(chunk, starts, widths, chunkHeight, lineStep, regular, size,
                                Align.from(block.align()), background, Colors.parse(Colors.BODY), border);
                        top -= chunkHeight;
                        if (!empty(cellLines)) {
                            newPage();
                            if (headerLines != null) {
                                drawTableRow(headerLines, starts, widths, headerHeight, lineStep, bold, size,
                                        Align.from(block.align()), Colors.parse(Colors.normalize(
                                                block.headerBackground(), Colors.PRIMARY)),
                                        Colors.parse(Colors.normalize(block.headerColor(), "#FFFFFF")), border);
                                top -= headerHeight;
                            }
                        }
                    }
                }
                index++;
            }
            top -= 12;
        }

        private void drawTableRow(List<List<String>> cellLines, float[] starts, float[] widths,
                                  float height, float lineStep, PDFont font, float size, Align align,
                                  Color background, Color textColor, Color border) throws IOException {
            float bottom = top - height;
            float right = starts[widths.length];
            for (int c = 0; c < widths.length; c++) {
                content.setNonStrokingColor(background);
                content.addRect(starts[c], bottom, widths[c], height);
                content.fill();
            }

            float ascent = fonts.ascent(font, size);
            for (int c = 0; c < widths.length; c++) {
                List<String> lines = c < cellLines.size() ? cellLines.get(c) : List.of("");
                float y = top - CELL_PADDING_Y - ascent;
                for (String line : lines) {
                    if (!line.isEmpty()) {
                        float width = fonts.width(font, size, line);
                        float x = switch (align) {
                            case CENTER -> starts[c] + (widths[c] - width) / 2f;
                            case RIGHT -> starts[c] + widths[c] - CELL_PADDING_X - width;
                            default -> starts[c] + CELL_PADDING_X;
                        };
                        text(font, size, x, y, line, textColor);
                    }
                    y -= lineStep;
                }
            }

            content.setStrokingColor(border);
            content.setLineWidth(0.6f);
            for (float boundary : starts) {
                content.moveTo(boundary, bottom);
                content.lineTo(boundary, top);
            }
            content.moveTo(starts[0], bottom);
            content.lineTo(right, bottom);
            content.moveTo(starts[0], top);
            content.lineTo(right, top);
            content.stroke();
        }

        private List<List<String>> wrapRow(List<String> values, int columns, float[] widths,
                                           PDFont font, float size) {
            List<List<String>> result = new ArrayList<>();
            for (int c = 0; c < columns; c++) {
                String value = c < values.size() && values.get(c) != null ? values.get(c) : "";
                result.add(wrapPlain(value, font, size, Math.max(10, widths[c] - CELL_PADDING_X * 2)));
            }
            return result;
        }

        private List<String> wrapPlain(String text, PDFont font, float size, float maxWidth) {
            List<String> lines = new ArrayList<>();
            String normalized = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
            for (String paragraph : normalized.split("\n", -1)) {
                String[] words = paragraph.split(" +");
                StringBuilder current = new StringBuilder();
                for (String word : words) {
                    if (word.isEmpty()) {
                        continue;
                    }
                    String candidate = current.isEmpty() ? word : current + " " + word;
                    if (fonts.width(font, size, candidate) <= maxWidth || current.isEmpty()) {
                        if (fonts.width(font, size, candidate) > maxWidth) {
                            if (!current.isEmpty()) {
                                lines.add(current.toString());
                                current.setLength(0);
                            }
                            for (String part : splitWord(word, font, size, maxWidth)) {
                                lines.add(part);
                            }
                        } else {
                            current.setLength(0);
                            current.append(candidate);
                        }
                    } else {
                        lines.add(current.toString());
                        current.setLength(0);
                        current.append(word);
                        if (fonts.width(font, size, word) > maxWidth) {
                            lines.removeLast();
                            for (String part : splitWord(word, font, size, maxWidth)) {
                                lines.add(part);
                            }
                            current.setLength(0);
                        }
                    }
                }
                lines.add(current.toString());
            }
            return lines;
        }

        private List<String> splitWord(String word, PDFont font, float size, float maxWidth) {
            List<String> parts = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                String candidate = current.toString() + c;
                if (fonts.width(font, size, candidate) > maxWidth && !current.isEmpty()) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
                current.append(c);
            }
            if (!current.isEmpty()) {
                parts.add(current.toString());
            }
            return parts;
        }

        private float rowHeight(List<List<String>> cells, float lineStep) {
            int lines = 1;
            for (List<String> cell : cells) {
                lines = Math.max(lines, cell.size());
            }
            return lines * lineStep + CELL_PADDING_Y * 2;
        }

        private boolean empty(List<List<String>> cells) {
            for (List<String> cell : cells) {
                if (!cell.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        private float[] columnWidths(TableBlock block, int columns) {
            List<Double> requested = block.columnWidths();
            double[] weights = new double[columns];
            double total = 0;
            if (requested.size() == columns) {
                for (int i = 0; i < columns; i++) {
                    Double value = requested.get(i);
                    weights[i] = value == null || value <= 0 ? 1 : value;
                    total += weights[i];
                }
            } else {
                for (int i = 0; i < columns; i++) {
                    weights[i] = 1;
                    total += 1;
                }
            }
            float[] widths = new float[columns];
            for (int i = 0; i < columns; i++) {
                widths[i] = (float) (contentWidth() * weights[i] / total);
            }
            return widths;
        }

        private void image(ImageBlock block) throws IOException {
            ImageLoader.LoadedImage image = imageLoader.load(block.src());
            MediaSizing.Size size = MediaSizing.image(image.width(), image.height(),
                    block.width(), block.height(), contentWidth(), contentHeight() - 30);
            ensure((float) size.height() + (block.caption() == null || block.caption().isBlank() ? 12 : 32));
            float x = alignedX((float) size.width(), Align.from(block.align()));
            float y = top - (float) size.height();
            PDImageXObject imageObject = PDImageXObject.createFromByteArray(document, image.png(), "image.png");
            content.drawImage(imageObject, x, y, (float) size.width(), (float) size.height());
            if (Boolean.TRUE.equals(block.border())) {
                content.setStrokingColor(Colors.parse(Colors.BORDER));
                content.setLineWidth(0.8f);
                content.addRect(x, y, (float) size.width(), (float) size.height());
                content.stroke();
            }
            top = y - 4;
            caption(block.caption());
            top -= 6;
        }

        private void chart(ChartBlock block) throws IOException {
            if (block.chart() == null) {
                throw new IllegalArgumentException("Chart block is missing the 'chart' object");
            }
            MediaSizing.Size size = MediaSizing.chart(block, contentWidth(), contentHeight() - 30);
            ensure((float) size.height() + (block.caption() == null || block.caption().isBlank() ? 12 : 32));
            float x = alignedX((float) size.width(), Align.from(block.align()));
            float y = top - (float) size.height();
            charts.draw(content, block.chart(), x, y, (float) size.width(), (float) size.height());
            top = y - 2;
            caption(block.caption());
            top -= 6;
        }

        private void caption(String caption) throws IOException {
            if (caption == null || caption.isBlank()) {
                return;
            }
            PDFont font = fonts.font(Styles.DEFAULT_FONT, false, true);
            float size = (float) Styles.CAPTION_SIZE;
            List<String> lines = wrapPlain(caption, font, size, contentWidth());
            float lineStep = size * 1.4f;
            top -= 2;
            for (String line : lines) {
                ensure(lineStep);
                float width = fonts.width(font, size, line);
                text(font, size, (float) page.margin() + (contentWidth() - width) / 2f, top - size, line,
                        Colors.parse(Colors.CAPTION));
                top -= lineStep;
            }
            top -= 4;
        }

        private float alignedX(float width, Align align) {
            float left = (float) page.margin();
            return switch (align) {
                case CENTER -> left + (contentWidth() - width) / 2f;
                case RIGHT -> left + contentWidth() - width;
                default -> left;
            };
        }

        private void text(PDFont font, float size, float x, float baseline, String value, Color color)
                throws IOException {
            if (value == null || value.isEmpty()) {
                return;
            }
            content.beginText();
            content.setFont(font, size);
            content.setNonStrokingColor(color);
            content.newLineAtOffset(x, baseline);
            content.showText(value);
            content.endText();
        }

        private void pageFooters() throws IOException {
            PDFont font = fonts.font(Styles.DEFAULT_FONT, false, false);
            float size = 8;
            for (int i = 0; i < pages.size(); i++) {
                String label = (i + 1) + " / " + pages.size();
                float width = fonts.width(font, size, label);
                try (PDPageContentStream footer = new PDPageContentStream(
                        document, pages.get(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                    footer.beginText();
                    footer.setFont(font, size);
                    footer.setNonStrokingColor(Colors.parse("#888888"));
                    footer.newLineAtOffset(((float) page.width() - width) / 2f, 24);
                    footer.showText(label);
                    footer.endText();
                }
            }
        }

        private void metadata() {
            PDDocumentInformation info = document.getDocumentInformation();
            info.setTitle(request.title());
            if (request.author() != null && !request.author().isBlank()) {
                info.setAuthor(request.author());
            }
            if (request.subject() != null && !request.subject().isBlank()) {
                info.setSubject(request.subject());
            }
            info.setCreator("SimonJP Document Generator");
            info.setProducer("Apache PDFBox");
        }

        private float contentWidth() {
            return (float) page.contentWidth();
        }

        private float contentHeight() {
            return (float) page.contentHeight();
        }
    }
}
