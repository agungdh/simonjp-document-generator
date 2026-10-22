package id.my.agungdh.document.support;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads the bundled Liberation fonts into a PDFBox document. The three families
 * are metric-compatible with Arial, Times New Roman and Courier New, so PDF
 * output is consistent and does not depend on system fonts.
 */
public final class PdfFonts {

    private static final Map<String, String> FILE_BASES = Map.of(
            "Liberation Sans", "LiberationSans",
            "Liberation Serif", "LiberationSerif",
            "Liberation Mono", "LiberationMono"
    );

    private final PDDocument document;
    private final Map<Face, PDType0Font> cache = new HashMap<>();

    public PdfFonts(PDDocument document) {
        this.document = document;
    }

    private record Face(String base, boolean bold, boolean italic) {
    }

    /**
     * Resolves a requested family to one of the bundled fonts and loads the
     * matching regular/bold/italic/bold-italic face.
     */
    public PDType0Font font(String family, boolean bold, boolean italic) {
        String base = FILE_BASES.get(Styles.pdfFont(family));
        Face face = new Face(base, bold, italic);
        return cache.computeIfAbsent(face, key -> load(key));
    }

    private PDType0Font load(Face face) {
        String style = face.bold()
                ? (face.italic() ? "BoldItalic" : "Bold")
                : (face.italic() ? "Italic" : "Regular");
        String resource = "/fonts/" + face.base() + "-" + style + ".ttf";
        try (InputStream in = PdfFonts.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Bundled font is missing: " + resource);
            }
            return PDType0Font.load(document, in, true);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load bundled font " + resource, e);
        }
    }

    public float width(PDFont font, double size, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        try {
            return font.getStringWidth(text) * (float) size / 1000f;
        } catch (IOException e) {
            throw new IllegalStateException("Could not measure text", e);
        }
    }

    public float ascent(PDFont font, double size) {
        float ascent = font.getFontDescriptor() == null ? 800 : font.getFontDescriptor().getAscent();
        return (ascent <= 0 ? 800 : ascent) * (float) size / 1000f;
    }

    public float descent(PDFont font, double size) {
        float descent = font.getFontDescriptor() == null ? -200 : font.getFontDescriptor().getDescent();
        if (descent >= 0) {
            descent = -200;
        }
        return -descent * (float) size / 1000f;
    }
}
