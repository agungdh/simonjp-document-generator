package id.my.agungdh.document.support;

import id.my.agungdh.document.model.DocumentRequest;

public record PageSpec(double width, double height, double margin) {

    public static PageSpec of(DocumentRequest request) {
        double width;
        double height;
        if ("LETTER".equalsIgnoreCase(request.pageSize())) {
            width = 612;
            height = 792;
        } else {
            width = 595.28;
            height = 841.89;
        }
        if ("landscape".equalsIgnoreCase(request.orientation())) {
            double tmp = width;
            width = height;
            height = tmp;
        }
        return new PageSpec(width, height, 48);
    }

    public double contentWidth() {
        return width - margin * 2;
    }

    public double contentHeight() {
        return height - margin * 2;
    }
}
