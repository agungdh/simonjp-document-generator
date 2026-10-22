package id.my.agungdh;

import id.my.agungdh.controller.HelloController;
import io.javalin.Javalin;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;

public class Main {

    private static final int PORT = 7000;

    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
            config.registerPlugin(new OpenApiPlugin(openApi -> openApi
                    .withDocumentationPath("/openapi")
                    .withPrettyOutput()
                    .withDefinitionConfiguration((version, schema) -> schema
                            .info(info -> info
                                    .title("SimonJP Document Generator API")
                                    .version("1.0.0")
                                    .description("REST API for generating documents.")))));

            config.registerPlugin(new SwaggerPlugin(swagger -> swagger
                    .withDocumentationPath("/openapi")
                    .withUiPath("/swagger")
                    .withTitle("SimonJP Document Generator API")));

            config.routes.get("/", ctx -> ctx.redirect("/swagger"));
            new HelloController().register(config.routes);
        });

        app.start(PORT);
    }
}
