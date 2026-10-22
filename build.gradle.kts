plugins {
    id("java")
    application
}

group = "id.my.agungdh"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val javalinVersion = "7.2.3"
val javalinOpenApiVersion = "7.2.3"
val poiVersion = "5.5.1"

dependencies {
    implementation("io.javalin:javalin:$javalinVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.1")

    // Office documents (Word, Excel, PowerPoint)
    implementation("org.apache.poi:poi:$poiVersion")
    implementation("org.apache.poi:poi-ooxml:$poiVersion")

    // Charts (rendered to PNG for PDF, native and editable in Word/Excel/PowerPoint)
    implementation("org.jfree:jfreechart:1.5.5")

    // PDF rendering from XHTML/CSS
    implementation("org.xhtmlrenderer:flying-saucer-pdf-openpdf:9.4.0")

    // OpenAPI spec generation (annotations + compile-time processor)
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:$javalinOpenApiVersion")
    annotationProcessor("io.javalin.community.openapi:openapi-annotation-processor:$javalinOpenApiVersion")

    // Swagger UI
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:$javalinOpenApiVersion")

    // Logging backend required by Javalin
    implementation("org.slf4j:slf4j-simple:2.0.18")

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
