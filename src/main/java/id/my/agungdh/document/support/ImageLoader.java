package id.my.agungdh.document.support;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Loads images from data URIs, http(s) URLs, or raw base64. Everything is
 * normalized to PNG so all renderers can embed it consistently.
 */
public final class ImageLoader {

    private static final int MAX_BYTES = 15 * 1024 * 1024;
    private static final int MAX_PIXELS = 40_000_000;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public record LoadedImage(byte[] png, int width, int height) {
    }

    public LoadedImage load(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Image block is missing the 'src' value");
        }
        String src = source.trim();
        byte[] raw;
        if (src.startsWith("data:")) {
            raw = fromDataUri(src);
        } else if (src.startsWith("http://") || src.startsWith("https://")) {
            raw = fromUrl(src);
        } else if (!src.contains("://") && src.length() >= 24) {
            raw = Images.decodeBase64(src);
            if (raw == null) {
                throw new IllegalArgumentException("Unsupported image source. Use a data URI or an http(s) URL.");
            }
        } else {
            throw new IllegalArgumentException("Unsupported image source. Use a data URI or an http(s) URL.");
        }

        if (raw.length > MAX_BYTES) {
            throw new IllegalArgumentException("Image is too large (max 15 MB)");
        }
        BufferedImage image = Images.read(raw);
        if (image == null) {
            throw new IllegalArgumentException("The image could not be decoded (supported: PNG, JPEG, GIF, BMP)");
        }
        long pixels = (long) image.getWidth() * image.getHeight();
        if (pixels > MAX_PIXELS) {
            throw new IllegalArgumentException("Image dimensions are too large");
        }
        return new LoadedImage(Images.toPng(image), image.getWidth(), image.getHeight());
    }

    private byte[] fromDataUri(String src) {
        int comma = src.indexOf(',');
        if (comma < 0) {
            throw new IllegalArgumentException("Malformed image data URI");
        }
        String header = src.substring(5, comma).toLowerCase(Locale.ROOT);
        if (!header.startsWith("image/")) {
            throw new IllegalArgumentException("Data URI must contain an image (image/png, image/jpeg, ...)");
        }
        String data = src.substring(comma + 1);
        if (!header.contains(";base64")) {
            throw new IllegalArgumentException("Only base64 data URIs are supported");
        }
        byte[] decoded = Images.decodeBase64(data);
        if (decoded == null) {
            throw new IllegalArgumentException("Invalid base64 image data");
        }
        return decoded;
    }

    private byte[] fromUrl(String src) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(src))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "simonjp-document-generator/1.0")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException("Image download failed with HTTP " + response.statusCode());
            }
            String contentType = response.headers().firstValue("content-type").orElse("");
            if (!contentType.isEmpty() && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                throw new IllegalArgumentException("URL did not return an image (content-type: " + contentType + ")");
            }
            try (InputStream in = response.body()) {
                byte[] bytes = in.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) {
                    throw new IllegalArgumentException("Image is too large (max 15 MB)");
                }
                return bytes;
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not download image: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Image download was interrupted", e);
        }
    }
}
