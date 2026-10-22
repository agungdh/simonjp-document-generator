package id.my.agungdh.document.support;

import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfObject;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfString;
import id.my.agungdh.document.model.DocumentRequest;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.Locale;

/** Adds PDF metadata and a small page number footer after layout. */
public final class PdfPostProcessor {

    public byte[] finish(byte[] pdf, DocumentRequest request) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(pdf.length + 4096);
        PdfReader reader = null;
        try {
            reader = new PdfReader(pdf);
            PdfStamper stamper = new PdfStamper(reader, out);
            PdfDictionary info = stamper.getWriter().getInfo();
            info.put(PdfName.TITLE, pdfString(request.title()));
            if (request.author() != null && !request.author().isBlank()) {
                info.put(PdfName.AUTHOR, pdfString(request.author()));
            }
            if (request.subject() != null && !request.subject().isBlank()) {
                info.put(PdfName.SUBJECT, pdfString(request.subject()));
            }
            info.put(PdfName.CREATOR, new PdfString("SimonJP Document Generator", PdfObject.TEXT_UNICODE));

            BaseFont font = BaseFont.createFont(PdfFonts.regularSans().toString(),
                    BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            int pages = reader.getNumberOfPages();
            for (int page = 1; page <= pages; page++) {
                String label = page + " / " + pages;
                float width = font.getWidthPoint(label, 8f);
                float x = (reader.getPageSize(page).getWidth() - width) / 2f;
                var canvas = stamper.getOverContent(page);
                canvas.beginText();
                canvas.setFontAndSize(font, 8f);
                canvas.setColorFill(new Color(0x88, 0x88, 0x88));
                canvas.setTextMatrix(x, 24);
                canvas.showText(label);
                canvas.endText();
            }
            stamper.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not finalize PDF: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private PdfString pdfString(String value) {
        return new PdfString(value == null ? "" : value, PdfObject.TEXT_UNICODE);
    }
}
