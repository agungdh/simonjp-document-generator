package id.my.agungdh.controller;

import id.my.agungdh.document.model.DocumentRequest;
import id.my.agungdh.document.service.DocumentGenerator;
import id.my.agungdh.document.support.DocumentFormat;
import id.my.agungdh.document.support.GeneratedDocument;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class DocumentController {

    private final DocumentGenerator generator = new DocumentGenerator();

    @OpenApi(
            path = "/api/documents/pdf",
            methods = HttpMethod.POST,
            summary = "Generate a PDF",
            description = "Renders the document model as a PDF with rich text, tables, images and charts.",
            tags = {"Documents"},
            requestBody = @OpenApiRequestBody(
                    description = "Document to render",
                    required = true,
                    content = @OpenApiContent(from = DocumentRequest.class, mimeType = "application/json")
            ),
            responses = {
                    @OpenApiResponse(status = "200", description = "PDF file",
                            content = @OpenApiContent(mimeType = "application/pdf")),
                    @OpenApiResponse(status = "400", description = "Invalid document",
                            content = @OpenApiContent(mimeType = "application/json"))
            }
    )
    public void pdf(Context ctx) {
        generate(ctx, DocumentFormat.PDF);
    }

    @OpenApi(
            path = "/api/documents/docx",
            methods = HttpMethod.POST,
            summary = "Generate a Word document",
            description = "Renders the document model as .docx, with native editable charts.",
            tags = {"Documents"},
            requestBody = @OpenApiRequestBody(
                    description = "Document to render",
                    required = true,
                    content = @OpenApiContent(from = DocumentRequest.class, mimeType = "application/json")
            ),
            responses = {
                    @OpenApiResponse(status = "200", description = "Word file",
                            content = @OpenApiContent(mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
                    @OpenApiResponse(status = "400", description = "Invalid document",
                            content = @OpenApiContent(mimeType = "application/json"))
            }
    )
    public void docx(Context ctx) {
        generate(ctx, DocumentFormat.DOCX);
    }

    @OpenApi(
            path = "/api/documents/xlsx",
            methods = HttpMethod.POST,
            summary = "Generate an Excel workbook",
            description = "Renders the document model onto a styled worksheet, with native editable Excel charts.",
            tags = {"Documents"},
            requestBody = @OpenApiRequestBody(
                    description = "Document to render",
                    required = true,
                    content = @OpenApiContent(from = DocumentRequest.class, mimeType = "application/json")
            ),
            responses = {
                    @OpenApiResponse(status = "200", description = "Excel file",
                            content = @OpenApiContent(mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
                    @OpenApiResponse(status = "400", description = "Invalid document",
                            content = @OpenApiContent(mimeType = "application/json"))
            }
    )
    public void xlsx(Context ctx) {
        generate(ctx, DocumentFormat.XLSX);
    }

    @OpenApi(
            path = "/api/documents/pptx",
            methods = HttpMethod.POST,
            summary = "Generate a PowerPoint presentation",
            description = "Flows the document model onto 16:9 slides, with native editable PowerPoint charts.",
            tags = {"Documents"},
            requestBody = @OpenApiRequestBody(
                    description = "Document to render",
                    required = true,
                    content = @OpenApiContent(from = DocumentRequest.class, mimeType = "application/json")
            ),
            responses = {
                    @OpenApiResponse(status = "200", description = "PowerPoint file",
                            content = @OpenApiContent(mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation")),
                    @OpenApiResponse(status = "400", description = "Invalid document",
                            content = @OpenApiContent(mimeType = "application/json"))
            }
    )
    public void pptx(Context ctx) {
        generate(ctx, DocumentFormat.PPTX);
    }

    @OpenApi(
            path = "/api/documents/sample",
            methods = HttpMethod.GET,
            summary = "Get a sample document request",
            description = "Returns a ready-to-send JSON body that demonstrates every block type.",
            tags = {"Documents"},
            responses = {
                    @OpenApiResponse(status = "200", description = "Sample document JSON",
                            content = @OpenApiContent(mimeType = "application/json"))
            }
    )
    public void sample(Context ctx) {
        try (InputStream in = DocumentController.class.getResourceAsStream("/sample-document.json")) {
            if (in == null) {
                throw new IllegalStateException("Sample document resource is missing");
            }
            ctx.contentType("application/json; charset=utf-8");
            ctx.result(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read sample document", e);
        }
    }

    private void generate(Context ctx, DocumentFormat format) {
        DocumentRequest request = ctx.bodyAsClass(DocumentRequest.class);
        GeneratedDocument document = generator.generate(request, format);
        ctx.header("Content-Disposition", "attachment; filename=\"" + document.filename() + "\"");
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.contentType(document.contentType());
        ctx.result(document.content());
    }

    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/documents/sample", this::sample);
        routes.post("/api/documents/pdf", this::pdf);
        routes.post("/api/documents/docx", this::docx);
        routes.post("/api/documents/xlsx", this::xlsx);
        routes.post("/api/documents/pptx", this::pptx);
    }
}
