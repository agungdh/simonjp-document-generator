package id.my.agungdh.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import id.my.agungdh.document.model.ChartBlock;
import id.my.agungdh.document.model.ChartSpec;
import id.my.agungdh.document.model.DocumentRequest;
import id.my.agungdh.document.model.ParagraphBlock;
import id.my.agungdh.document.service.DocumentGenerator;
import id.my.agungdh.document.support.DocumentFormat;
import id.my.agungdh.document.support.GeneratedDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentGenerationTest {

    private final DocumentGenerator generator = new DocumentGenerator();

    @Test
    void sampleDocumentCanBeDeserialized() throws IOException {
        DocumentRequest request = sampleRequest();
        assertEquals("Laporan Penjualan 2026", request.title());
        assertTrue(request.blocks().size() >= 8);
        assertTrue(request.blocks().stream().anyMatch(ChartBlock.class::isInstance));
        ParagraphBlock paragraph = request.blocks().stream()
                .filter(ParagraphBlock.class::isInstance)
                .map(ParagraphBlock.class::cast)
                .findFirst()
                .orElseThrow();
        assertTrue(paragraph.runs().size() > 4);
    }

    @Test
    void generatesPdf() throws IOException {
        GeneratedDocument document = generate(DocumentFormat.PDF);
        String magic = new String(document.content(), 0, 5, StandardCharsets.US_ASCII);
        assertEquals("%PDF-", magic);
        assertTrue(document.content().length > 10_000);
        assertTrue(document.filename().endsWith(".pdf"));
    }

    @Test
    void generatesWordWithNativeChart() throws IOException {
        GeneratedDocument document = generate(DocumentFormat.DOCX);
        assertZipMagic(document.content());
        assertTrue(document.content().length > 10_000);
        assertTrue(zipContains(document.content(), "word/charts/chart1.xml"));
        assertTrue(zipContainsText(document.content(), "word/charts/chart1.xml", "Penjualan per Kuartal"));
    }

    @Test
    void generatesExcelWithNativeChart() throws IOException {
        GeneratedDocument document = generate(DocumentFormat.XLSX);
        assertZipMagic(document.content());
        assertTrue(document.content().length > 10_000);
        assertTrue(zipContains(document.content(), "xl/charts/chart1.xml"));
        assertTrue(zipContainsText(document.content(), "xl/charts/chart1.xml", "Penjualan per Kuartal"));
    }

    @Test
    void generatesPowerPointWithNativeChart() throws IOException {
        GeneratedDocument document = generate(DocumentFormat.PPTX);
        assertZipMagic(document.content());
        assertTrue(document.content().length > 10_000);
        assertTrue(zipContains(document.content(), "ppt/charts/chart1.xml"));
    }

    @Test
    void rejectsChartWithMismatchedValues() {
        DocumentRequest request = new DocumentRequest(
                "Invalid chart", null, null, null, "A4", "portrait",
                List.of(new ChartBlock(
                        new ChartSpec("bar", "Broken", null, null,
                                List.of("A", "B"), List.of(new id.my.agungdh.document.model.ChartSeries("S", List.of(1.0))),
                                true, List.of(), null),
                        null, null, "center", null)));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> generator.generate(request, DocumentFormat.PDF));
        assertTrue(error.getMessage().contains("exactly 2 numbers"));
    }

    @Test
    void rejectsUnknownChartType() {
        DocumentRequest request = new DocumentRequest(
                "Invalid chart", null, null, null, "A4", "portrait",
                List.of(new ChartBlock(
                        new ChartSpec("radar-3d", "Broken", null, null,
                                List.of("A"), List.of(new id.my.agungdh.document.model.ChartSeries("S", List.of(1.0))),
                                true, List.of(), null),
                        null, null, "center", null)));
        assertThrows(IllegalArgumentException.class,
                () -> generator.generate(request, DocumentFormat.DOCX));
    }

    private GeneratedDocument generate(DocumentFormat format) throws IOException {
        GeneratedDocument document = generator.generate(sampleRequest(), format);
        Path directory = Path.of("build", "test-documents");
        Files.createDirectories(directory);
        Files.write(directory.resolve(document.filename()), document.content());
        assertNotNull(document.content());
        return document;
    }

    private DocumentRequest sampleRequest() throws IOException {
        try (var in = getClass().getResourceAsStream("/sample-document.json")) {
            assertNotNull(in);
            return new ObjectMapper().readValue(in, DocumentRequest.class);
        }
    }

    private void assertZipMagic(byte[] bytes) {
        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);
        assertEquals(3, bytes[2]);
        assertEquals(4, bytes[3]);
    }

    private boolean zipContains(byte[] bytes, String name) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean zipContainsText(byte[] bytes, String name, String expected) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals(name)) {
                    String content = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    return content.contains(expected);
                }
            }
        }
        return false;
    }
}
