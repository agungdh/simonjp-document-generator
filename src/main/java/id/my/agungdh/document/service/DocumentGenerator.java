package id.my.agungdh.document.service;

import id.my.agungdh.document.model.DocumentRequest;
import id.my.agungdh.document.render.DocxDocumentRenderer;
import id.my.agungdh.document.render.PdfDocumentRenderer;
import id.my.agungdh.document.render.PptxDocumentRenderer;
import id.my.agungdh.document.render.XlsxDocumentRenderer;
import id.my.agungdh.document.support.DocumentFormat;
import id.my.agungdh.document.support.GeneratedDocument;
import id.my.agungdh.document.support.Names;

public final class DocumentGenerator {

    private final DocumentValidator validator = new DocumentValidator();

    public GeneratedDocument generate(DocumentRequest request, DocumentFormat format) {
        validator.validate(request);
        byte[] bytes = switch (format) {
            case PDF -> new PdfDocumentRenderer().render(request);
            case DOCX -> new DocxDocumentRenderer().render(request);
            case XLSX -> new XlsxDocumentRenderer().render(request);
            case PPTX -> new PptxDocumentRenderer().render(request);
        };
        String filename = Names.fileBaseName(request.title()) + "." + format.extension();
        return new GeneratedDocument(bytes, filename, format.contentType());
    }
}
