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
import id.my.agungdh.document.support.ChartImageRenderer;
import id.my.agungdh.document.support.Colors;
import id.my.agungdh.document.support.Html;
import id.my.agungdh.document.support.ImageLoader;
import id.my.agungdh.document.support.MediaSizing;
import id.my.agungdh.document.support.PageSpec;
import id.my.agungdh.document.support.PdfFonts;
import id.my.agungdh.document.support.PdfPostProcessor;
import id.my.agungdh.document.support.Styles;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * PDF rendering: the document model becomes semantic XHTML + CSS, which
 * Flying Saucer lays out with the bundled Liberation fonts. Charts are
 * rasterized with JFreeChart and embedded as high-resolution PNGs.
 */
public final class PdfDocumentRenderer {

    private final ImageLoader imageLoader = new ImageLoader();
    private final ChartImageRenderer chartRenderer = new ChartImageRenderer();
    private final PdfPostProcessor postProcessor = new PdfPostProcessor();

    public byte[] render(DocumentRequest request) {
        PageSpec page = PageSpec.of(request);
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("simonjp-pdf-");
            String html = buildHtml(request, page, workDir);
            ITextRenderer renderer = new ITextRenderer();
            PdfFonts.register(renderer.getFontResolver());
            renderer.setDocumentFromString(html, workDir.toUri().toString());
            renderer.layout();
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            renderer.createPDF(out);
            return postProcessor.finish(out.toByteArray(), request);
        } catch (IOException e) {
            throw new IllegalStateException("Could not render PDF: " + e.getMessage(), e);
        } finally {
            deleteRecursively(workDir);
        }
    }

    private String buildHtml(DocumentRequest request, PageSpec page, Path workDir) throws IOException {
        StringBuilder body = new StringBuilder(16 * 1024);
        int mediaIndex = 0;
        for (DocumentBlock block : request.blocks()) {
            if (block instanceof HeadingBlock heading) {
                body.append(heading(heading, page));
            } else if (block instanceof ParagraphBlock paragraph) {
                body.append(paragraph(paragraph));
            } else if (block instanceof TableBlock table) {
                body.append(table(table));
            } else if (block instanceof ImageBlock image) {
                body.append(image(image, page, workDir, mediaIndex++));
            } else if (block instanceof ChartBlock chart) {
                body.append(chart(chart, page, workDir, mediaIndex++));
            } else if (block instanceof PageBreakBlock) {
                body.append("<div style=\"page-break-before: always; height: 0;\"></div>");
            }
        }

        return """
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="utf-8"/>
                <title>%s</title>
                <style>
                @page { size: %.2fpt %.2fpt; margin: %.2fpt; }
                body { font-family: 'Liberation Sans'; font-size: 11pt; color: #222222; line-height: 1.35; }
                p { margin: 0 0 8pt 0; }
                h1, h2, h3, h4 { font-weight: bold; color: #1F4E79; page-break-after: avoid; }
                table { border-collapse: collapse; width: 100%%; }
                thead { display: table-header-group; }
                tr { page-break-inside: avoid; }
                .figure { page-break-inside: avoid; }
                .caption { font-size: 9pt; color: #666666; margin-top: 3pt; }
                </style>
                </head>
                <body>
                %s
                </body>
                </html>
                """.formatted(Html.escape(request.title()), page.width(), page.height(), page.margin(), body);
    }

    private String heading(HeadingBlock block, PageSpec page) {
        String color = Colors.normalize(block.color(), Colors.PRIMARY);
        double size = Styles.size(block.fontSize(), Styles.headingSize(block.level()));
        String family = Styles.pdfFont(block.fontFamily());
        String align = alignCss(Align.from(block.align()));
        String tag = "h" + block.level();
        return "<%s style=\"font-family:'%s';font-size:%.2fpt;color:%s;text-align:%s;margin:0 0 %.2fpt 0;line-height:1.2;\">%s</%s>\n"
                .formatted(tag, family, size, color, align, block.marginAfter(), Html.withLineBreaks(block.text()), tag);
    }

    private String paragraph(ParagraphBlock block) {
        Align align = Align.from(block.align());
        String family = Styles.pdfFont(block.fontFamily());
        double size = Styles.size(block.fontSize(), Styles.BODY_SIZE);
        String color = Colors.normalize(block.color(), Colors.BODY);
        StringBuilder sb = new StringBuilder();
        sb.append("<p style=\"text-align:").append(alignCss(align))
                .append(";font-family:'").append(family)
                .append("';font-size:").append(format(size)).append("pt")
                .append(";color:").append(color)
                .append(";line-height:").append(format(block.lineSpacing()))
                .append(";margin:0 0 ").append(format(block.marginAfter())).append("pt 0;\">");
        if (block.runs().isEmpty()) {
            sb.append("&nbsp;");
        } else {
            for (TextRun run : block.runs()) {
                sb.append(run(run, family, size, color));
            }
        }
        sb.append("</p>\n");
        return sb.toString();
    }

    private String run(TextRun run, String inheritedFamily, double inheritedSize, String inheritedColor) {
        StringBuilder style = new StringBuilder();
        if (run.fontFamily() != null && !run.fontFamily().isBlank()) {
            style.append("font-family:'").append(Styles.pdfFont(run.fontFamily())).append("';");
        }
        if (run.fontSize() != null) {
            style.append("font-size:").append(format(Styles.size(run.fontSize(), inheritedSize))).append("pt;");
        }
        if (run.bold()) {
            style.append("font-weight:bold;");
        }
        if (run.italic()) {
            style.append("font-style:italic;");
        }
        if (run.underline()) {
            style.append("text-decoration:underline;");
        }
        if (run.color() != null) {
            style.append("color:").append(Colors.normalize(run.color(), inheritedColor)).append(";");
        }
        if (style.isEmpty()) {
            return Html.withLineBreaks(run.text());
        }
        return "<span style=\"" + style + "\">" + Html.withLineBreaks(run.text()) + "</span>";
    }

    private String table(TableBlock block) {
        int columns = block.columnCount();
        if (columns == 0) {
            return "";
        }
        String family = Styles.pdfFont(block.fontFamily());
        double size = Styles.size(block.fontSize(), Styles.TABLE_SIZE);
        String headerBg = Colors.normalize(block.headerBackground(), Colors.PRIMARY);
        String headerColor = Colors.normalize(block.headerColor(), "#FFFFFF");
        String border = Colors.normalize(block.borderColor(), Colors.BORDER);
        String stripe = Colors.normalize(block.stripeColor(), "#F2F2F2");
        String align = alignCss(Align.from(block.align()));
        List<Double> widths = columnPercentages(block, columns);

        StringBuilder sb = new StringBuilder(2048);
        sb.append("<table style=\"margin:4pt 0 12pt 0;font-family:'").append(family)
                .append("';font-size:").append(format(size)).append("pt;\">");
        sb.append("<colgroup>");
        for (Double width : widths) {
            sb.append("<col style=\"width:").append(format(width)).append("%\"/>");
        }
        sb.append("</colgroup>");

        if (!block.headers().isEmpty()) {
            sb.append("<thead><tr>");
            for (int c = 0; c < columns; c++) {
                String text = c < block.headers().size() ? block.headers().get(c) : "";
                sb.append("<th style=\"background-color:").append(headerBg)
                        .append(";color:").append(headerColor)
                        .append(";font-weight:bold;text-align:left;vertical-align:middle;")
                        .append("border:0.6pt solid ").append(border)
                        .append(";padding:4pt 6pt;\">").append(Html.withLineBreaks(text)).append("</th>");
            }
            sb.append("</tr></thead>");
        }
        sb.append("<tbody>");
        int rowIndex = 0;
        for (List<String> row : block.rows()) {
            String background = rowIndex % 2 == 1 ? stripe : "#FFFFFF";
            sb.append("<tr>");
            for (int c = 0; c < columns; c++) {
                String text = c < row.size() ? row.get(c) : "";
                sb.append("<td style=\"background-color:").append(background)
                        .append(";text-align:").append(align)
                        .append(";vertical-align:top;border:0.6pt solid ").append(border)
                        .append(";padding:4pt 6pt;\">").append(Html.withLineBreaks(text)).append("</td>");
            }
            sb.append("</tr>");
            rowIndex++;
        }
        sb.append("</tbody></table>\n");
        return sb.toString();
    }

    private String image(ImageBlock block, PageSpec page, Path workDir, int index) throws IOException {
        ImageLoader.LoadedImage image = imageLoader.load(block.src());
        MediaSizing.Size size = MediaSizing.image(image.width(), image.height(),
                block.width(), block.height(), page.contentWidth(), page.contentHeight());
        String file = "image-" + index + ".png";
        Files.write(workDir.resolve(file), image.png());
        return figure(file, size, block.align(), block.caption(), block.border());
    }

    private String chart(ChartBlock block, PageSpec page, Path workDir, int index) throws IOException {
        if (block.chart() == null) {
            throw new IllegalArgumentException("Chart block is missing the 'chart' object");
        }
        MediaSizing.Size size = MediaSizing.chart(block, page.contentWidth(), page.contentHeight());
        byte[] png = chartRenderer.render(block.chart(),
                (int) Math.ceil(size.width() * 2), (int) Math.ceil(size.height() * 2));
        String file = "chart-" + index + ".png";
        Files.write(workDir.resolve(file), png);
        return figure(file, size, block.align(), block.caption(), false);
    }

    private String figure(String file, MediaSizing.Size size, String align,
                          String caption, boolean border) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"figure\" style=\"text-align:").append(alignCss(Align.from(align)))
                .append(";margin:6pt 0 10pt 0;\">");
        sb.append("<img src=\"").append(file).append("\" style=\"width:").append(format(size.width()))
                .append("pt;height:").append(format(size.height())).append("pt;");
        if (border) {
            sb.append("border:0.8pt solid ").append(Colors.BORDER).append(";");
        }
        sb.append("\"/>");
        if (caption != null && !caption.isBlank()) {
            sb.append("<div class=\"caption\">").append(Html.escape(caption)).append("</div>");
        }
        sb.append("</div>\n");
        return sb.toString();
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

    private String alignCss(Align align) {
        return switch (align) {
            case CENTER -> "center";
            case RIGHT -> "right";
            case JUSTIFY -> "justify";
            default -> "left";
        };
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private void deleteRecursively(Path dir) {
        if (dir == null) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }
}
