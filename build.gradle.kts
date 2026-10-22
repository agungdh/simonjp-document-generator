plugins {
    id("java")
    application
}

group = "id.my.agungdh"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val javalinVersion = "6.7.0"
val javalinOpenApiVersion = "6.7.0-1"

dependencies {
    implementation("io.javalin:javalin:$javalinVersion")

    // OpenAPI spec generation (annotations + compile-time processor)
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:$javalinOpenApiVersion")
    annotationProcessor("io.javalin.community.openapi:openapi-annotation-processor:$javalinOpenApiVersion")

    // Swagger UI
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:$javalinOpenApiVersion")

    // Logging backend required by Javalin
    implementation("org.slf4j:slf4j-simple:2.0.17")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "id.my.agungdh.Main"
}

tasks.test {
    useJUnitPlatform()
}
