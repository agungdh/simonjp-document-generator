package id.my.agungdh.document.support;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class Images {

    private Images() {
    }

    public static BufferedImage read(byte[] bytes) {
        try {
            ImageIO.setUseCache(false);
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            return null;
        }
    }

    public static byte[] toPng(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode image as PNG", e);
        }
    }

    public static BufferedImage scale(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(
                Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, target.getWidth(), target.getHeight(), null);
        } finally {
            g.dispose();
        }
        return target;
    }

    public static byte[] decodeBase64(String value) {
        try {
            return Base64.getMimeDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
