package com.dockmind.ai;

import java.math.BigDecimal;
import java.util.List;

/**
 * A reader's answer after validation, the only form that may reach the database.
 * When {@code manual} is true, {@code poNumber} is null and no line is linked.
 */
public record ValidatedExtraction(
        boolean manual, String poNumber, String supplier, String deliveryNoteNo, Double poConfidence, String freeText,
        Double overallConfidence, String aiNote, List<Line> lines) {

    /** {@code qty} is null when the quantity could not be read. */
    public record Line(String description, String poLineId, BigDecimal qty, String unit, Double confidence) {
    }

    public static ValidatedExtraction failed(String note) {
        return new ValidatedExtraction(true, null, null, null, null, null, null, note, List.of());
    }
}
