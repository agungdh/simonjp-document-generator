package id.my.agungdh.document.support;

import com.lowagie.text.pdf.BaseFont;
import org.xhtmlrenderer.pdf.ITextFontResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/**
 * Liberation fonts are metric-compatible with Arial, Times New Roman and
 * Courier New, and are bundled so PDF output looks the same everywhere.
 */
public final class PdfFonts {

    private static final List<String> STYLES = List.of("Regular", "Bold", "Italic", "BoldItalic");
    private static final Map<String, String> FILES = Map.of(
            "Liberation Sans", "LiberationSans",
            "Liberation Serif", "LiberationSerif",
            "Liberation Mono", "LiberationMono"
    );

    private static volatile Path directory;

    private PdfFonts() {
    }

    public static synchronized Path directory() {
        if (directory != null) {
            return directory;
        }
        try {
            Path dir = Files.createTempDirectory("simonjp-pdf-fonts-");
            for (String fileBase : FILES.values()) {
                for (String style : STYLES) {
                    String name = fileBase + "-" + style + ".ttf";
                    try (InputStream in = PdfFonts.class.getResourceAsStream("/fonts/" + name)) {
                        if (in == null) {
                            throw new IllegalStateException("Bundled font is missing: " + name);
                        }
                        Path target = dir.resolve(name);
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                        target.toFile().deleteOnExit();
                    }
                }
            }
            dir.toFile().deleteOnExit();
            directory = dir;
            return dir;
        } catch (IOException e) {
            throw new IllegalStateException("Could not unpack bundled PDF fonts", e);
        }
    }

    public static void register(ITextFontResolver resolver) {
        Path dir = directory();
        try {
            for (Map.Entry<String, String> entry : FILES.entrySet()) {
                String family = entry.getKey();
                String fileBase = entry.getValue();
                for (String style : STYLES) {
                    Path font = dir.resolve(fileBase + "-" + style + ".ttf");
                    resolver.addFont(font.toString(), family, BaseFont.IDENTITY_H, true, null);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not register bundled PDF fonts", e);
        }
    }

    public static Path regularSans() {
        return directory().resolve("LiberationSans-Regular.ttf");
    }
}
