package id.my.agungdh.controller;

import id.my.agungdh.dto.GreetingResponse;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;

public class HelloController {

    @OpenApi(
            path = "/api/hello",
            methods = HttpMethod.GET,
            summary = "Say hello",
            description = "Returns a greeting for the given name.",
            tags = {"Hello"},
            queryParams = {
                    @OpenApiParam(
                            name = "name",
                            description = "Name to greet",
                            required = false
                    )
            },
            responses = {
                    @OpenApiResponse(
                            status = "200",
                            description = "Successful greeting",
                            content = @OpenApiContent(from = GreetingResponse.class)
                    )
            }
    )
    public void hello(Context ctx) {
        String name = ctx.queryParamAsClass("name", String.class).getOrDefault("World");
        ctx.json(new GreetingResponse("Hello, " + name + "!"));
    }

    public void register(JavalinDefaultRoutingApi routes) {
        routes.get("/api/hello", this::hello);
    }
}
