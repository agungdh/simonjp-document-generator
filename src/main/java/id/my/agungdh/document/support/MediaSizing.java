package id.my.agungdh.document.support;

import id.my.agungdh.document.model.ChartBlock;

public final class MediaSizing {

    public record Size(double width, double height) {
    }

    private MediaSizing() {
    }

    public static Size image(int pixelWidth, int pixelHeight, Double requestedWidth,
                             Double requestedHeight, double maxWidth, double maxHeight) {
        double naturalWidth = pixelWidth * 72.0 / 96.0;
        double naturalHeight = pixelHeight * 72.0 / 96.0;
        if (naturalWidth <= 0 || naturalHeight <= 0) {
            naturalWidth = 320;
            naturalHeight = 180;
        }
        double width;
        double height;
        if (requestedWidth == null && requestedHeight == null) {
            width = naturalWidth;
            height = naturalHeight;
        } else if (requestedHeight == null) {
            width = requestedWidth;
            height = width * naturalHeight / naturalWidth;
        } else if (requestedWidth == null) {
            height = requestedHeight;
            width = height * naturalWidth / naturalHeight;
        } else {
            width = requestedWidth;
            height = requestedHeight;
        }
        double scale = Math.min(1.0, Math.min(maxWidth / width, maxHeight / height));
        return new Size(Math.max(1, width * scale), Math.max(1, height * scale));
    }

    public static Size chart(ChartBlock block, double maxWidth, double maxHeight) {
        double width = block.width() == null ? Math.min(maxWidth, 480) : block.width();
        double height = block.height() == null ? width * 9.0 / 16.0 : block.height();
        double scale = Math.min(1.0, Math.min(maxWidth / width, maxHeight / height));
        return new Size(Math.max(1, width * scale), Math.max(1, height * scale));
    }
}
