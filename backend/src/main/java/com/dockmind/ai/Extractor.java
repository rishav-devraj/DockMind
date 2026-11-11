package com.dockmind.ai;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reads a delivery note. Implementations return the reader's raw text and do no checking of their own:
 * every answer, whichever extractor produced it, goes through {@link ExtractionValidator}.
 * The reader gets the photo only, never the list of purchase orders: it reads, and code looks the PO up.
 */
public interface Extractor {
    String extract(ExtractionRequest request) throws Exception;

    record ExtractionRequest(byte[] image, String mimeType, String fileName, String fixtureHeader) {
    }

    record OpenPo(String poNumber, String supplier, List<OpenPoLine> lines) {
    }

    record OpenPoLine(String id, String description, BigDecimal orderedQty, String unit) {
    }
}
