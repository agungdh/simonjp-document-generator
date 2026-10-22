package id.my.agungdh;

import id.my.agungdh.controller.HelloController;
import io.javalin.Javalin;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;

public class Main {

    private static final int DEFAULT_PORT = 7000;

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

        app.start(resolvePort(args));
    }

    private static int resolvePort(String[] args) {
        if (args.length > 0) {
            return parsePort(args[0]);
        }

        String env = System.getenv("PORT");
        if (env != null && !env.isBlank()) {
            return parsePort(env);
        }

        return DEFAULT_PORT;
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value.trim());
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 0 and 65535, got: " + value);
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port: " + value, e);
        }
    }
}
