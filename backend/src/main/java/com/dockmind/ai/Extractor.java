package com.dockmind.ai;

import java.util.List;

/**
 * Reads a delivery note. Implementations return the reader's raw text and do no checking of their own:
 * every answer, whichever extractor produced it, goes through {@link ExtractionValidator}.
 */
public interface Extractor {
    String extract(ExtractionRequest request) throws Exception;

    record ExtractionRequest(byte[] image, String mimeType, String fileName, String fixtureHeader, List<OpenPo> openPos) {
    }

    record OpenPo(String poNumber, String supplier, List<OpenPoLine> lines) {
    }

    record OpenPoLine(String id, String description, java.math.BigDecimal orderedQty, String unit) {
    }
}
