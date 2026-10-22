# syntax=docker/dockerfile:1

# Runtime image: Debian 13 (trixie) + Java 25 JRE, no shell and no package manager.
# The "nonroot" tag runs the process as uid/gid 65532 instead of root.
FROM gcr.io/distroless/java25-debian13:nonroot

WORKDIR /app

# Build the runnable fat jar first (the jar bundles every dependency and the fonts):
#   ./gradlew shadowJar
COPY --chown=nonroot:nonroot build/libs/simonjp-document-generator-1.0-SNAPSHOT-all.jar /app/app.jar

# Main.resolvePort() reads PORT; 7000 is the application default.
ENV PORT=7000

EXPOSE 7000

USER nonroot:nonroot

# Apache POI / PDFBox only need headless AWT; the container has no display server.
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
