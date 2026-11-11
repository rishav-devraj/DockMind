package com.dockmind.api;

import com.dockmind.domain.Receipt;
import com.dockmind.domain.ReceiptLine;
import com.dockmind.domain.ReceiptStatus;
import com.dockmind.matching.Discrepancy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** The one place a receipt is turned into its JSON shape. Field order follows CONTRACT.md. */
public record ReceiptView(
        String id, ReceiptStatus status, String poNumber, String supplier, Double overallConfidence, String aiNote,
        List<Line> lines, String deliveryNoteNo, Double poConfidence, String freeText, boolean needsReview,
        String disputeReason, String clerk, Instant createdAt, Instant updatedAt) {

    private static final String RECEIPT_PREFIX = "RCP-";
    private static final String LINE_PREFIX = "RL-";

    public record Line(
            String id, String poLineId, String description, BigDecimal orderedQty, BigDecimal receivedQty, String unit,
            Double confidence, Discrepancy discrepancy, BigDecimal delta, BigDecimal aiQty, boolean correctedByClerk,
            boolean needsReview) {

        static Line from(ReceiptLine line) {
            return new Line(LINE_PREFIX + line.getId(), line.getPoLineId(), line.getDescription(),
                    plain(line.getOrderedQty()), plain(line.getReceivedQty()), line.getUnit(), line.getConfidence(),
                    line.getDiscrepancy(), plain(line.getDelta()), plain(line.getAiQty()), line.isCorrectedByClerk(),
                    line.isNeedsReview());
        }
    }

    public static ReceiptView from(Receipt receipt) {
        return new ReceiptView(RECEIPT_PREFIX + receipt.getId(), receipt.getStatus(), receipt.getPoNumber(),
                receipt.getSupplier(), receipt.getOverallConfidence(), receipt.getAiNote(),
                receipt.getLines().stream().map(Line::from).toList(), receipt.getDeliveryNoteNo(),
                receipt.getPoConfidence(), receipt.getFreeText(), receipt.isNeedsReview(), receipt.getDisputeReason(),
                receipt.getClerk(), receipt.getCreatedAt(), receipt.getUpdatedAt());
    }

    public static Long parseReceiptId(String id) {
        return parse(id, RECEIPT_PREFIX, ApiException.receiptNotFound());
    }

    public static Long parseLineId(String id) {
        return parse(id, LINE_PREFIX, ApiException.lineNotFound());
    }

    private static Long parse(String id, String prefix, ApiException notFound) {
        if (id == null || !id.matches(prefix + "\\d{1,18}")) {
            throw notFound;
        }
        return Long.valueOf(id.substring(prefix.length()));
    }

    /** The database stores three decimals; the API shows 80, not 80.000. */
    static BigDecimal plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }
}
