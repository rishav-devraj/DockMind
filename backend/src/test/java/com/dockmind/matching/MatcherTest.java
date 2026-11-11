package com.dockmind.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.dockmind.matching.Matcher.OrderLine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MatcherTest {
    private final Matcher matcher = new Matcher(0.80);
    private static final OrderLine SALMON = new OrderLine("L1", "Salmon fillet", new BigDecimal("80"), "KG");
    private static final OrderLine PRAWNS = new OrderLine("L2", "Tiger Prawns", new BigDecimal("40"), "KG");

    static class TestLine implements Matcher.Line {
        String poLineId;
        String description;
        BigDecimal orderedQty;
        BigDecimal receivedQty;
        String unit;
        Double confidence;
        boolean corrected;
        Discrepancy discrepancy;
        BigDecimal delta;
        boolean needsReview;

        static TestLine of(String poLineId, String qty, String unit) {
            TestLine line = new TestLine();
            line.poLineId = poLineId;
            line.description = "item";
            line.receivedQty = new BigDecimal(qty);
            line.unit = unit;
            line.confidence = 0.95;
            return line;
        }

        @Override public String getPoLineId() { return poLineId; }
        @Override public void setPoLineId(String v) { poLineId = v; }
        @Override public String getDescription() { return description; }
        @Override public void setDescription(String v) { description = v; }
        @Override public BigDecimal getOrderedQty() { return orderedQty; }
        @Override public void setOrderedQty(BigDecimal v) { orderedQty = v; }
        @Override public BigDecimal getReceivedQty() { return receivedQty; }
        @Override public void setReceivedQty(BigDecimal v) { receivedQty = v; }
        @Override public String getUnit() { return unit; }
        @Override public void setUnit(String v) { unit = v; }
        @Override public Double getConfidence() { return confidence; }
        @Override public boolean isCorrectedByClerk() { return corrected; }
        @Override public void setDiscrepancy(Discrepancy v) { discrepancy = v; }
        @Override public void setDelta(BigDecimal v) { delta = v; }
        @Override public void setNeedsReview(boolean v) { needsReview = v; }
    }

    private TestLine classify(TestLine line, OrderLine order) {
        matcher.classify(line, order);
        return line;
    }

    @Test
    void equalQuantityIsMatch() {
        TestLine line = classify(TestLine.of("L1", "80", "KG"), SALMON);
        assertThat(line.discrepancy).isEqualTo(Discrepancy.MATCH);
        assertThat(line.delta).isEqualByComparingTo("0");
    }

    @Test
    void equalQuantityWithDifferentScaleIsMatch() {
        assertThat(classify(TestLine.of("L1", "80.000", "KG"), SALMON).discrepancy).isEqualTo(Discrepancy.MATCH);
    }

    @Test
    void belowOrderedIsShort() {
        TestLine line = classify(TestLine.of("L1", "72", "KG"), SALMON);
        assertThat(line.discrepancy).isEqualTo(Discrepancy.SHORT);
        assertThat(line.delta).isEqualByComparingTo("-8");
        assertThat(line.orderedQty).isEqualByComparingTo("80");
    }

    @Test
    void aboveOrderedIsOver() {
        TestLine line = classify(TestLine.of("L1", "85.5", "KG"), SALMON);
        assertThat(line.discrepancy).isEqualTo(Discrepancy.OVER);
        assertThat(line.delta).isEqualByComparingTo("5.5");
    }

    @Test
    void differentUnitIsUnitMismatchWithNoDelta() {
        TestLine line = classify(TestLine.of("L1", "80", "CASE"), SALMON);
        assertThat(line.discrepancy).isEqualTo(Discrepancy.UNIT_MISMATCH);
        assertThat(line.delta).isNull();
        assertThat(line.orderedQty).isEqualByComparingTo("80");
    }

    @Test
    void missingUnitIsUnitMismatch() {
        assertThat(classify(TestLine.of("L1", "80", null), SALMON).discrepancy).isEqualTo(Discrepancy.UNIT_MISMATCH);
    }

    @Test
    void unitMismatchWinsOverZeroQuantity() {
        assertThat(classify(TestLine.of("L1", "0", "CASE"), SALMON).discrepancy).isEqualTo(Discrepancy.UNIT_MISMATCH);
    }

    @Test
    void noLinkIsUnknownItemWithNullOrderedAndDelta() {
        TestLine line = classify(TestLine.of(null, "10", "KG"), null);
        assertThat(line.discrepancy).isEqualTo(Discrepancy.UNKNOWN_ITEM);
        assertThat(line.orderedQty).isNull();
        assertThat(line.delta).isNull();
    }

    @Test
    void zeroReceivedIsNotDeliveredWithNegativeOrderedDelta() {
        TestLine line = classify(TestLine.of("L1", "0", "KG"), SALMON);
        assertThat(line.discrepancy).isEqualTo(Discrepancy.NOT_DELIVERED);
        assertThat(line.delta).isEqualByComparingTo("-80");
    }

    @ParameterizedTest
    @CsvSource({"KGS,KG", "kg,KG", "' Kgs. ',KG", "CASES,CASE", "cases,CASE", "EACH,EA", "PCS,EA", "ea,EA", "TRAYS,TRAY", "BOX,BOX"})
    void normalizesUnits(String raw, String expected) {
        assertThat(Matcher.normalizeUnit(raw)).isEqualTo(expected);
    }

    @Test
    void normalizedUnitsCompareEqual() {
        TestLine line = classify(TestLine.of("L1", "80", "kgs"), SALMON);
        assertThat(line.discrepancy).isEqualTo(Discrepancy.MATCH);
        assertThat(line.unit).isEqualTo("KG");
    }

    @Test
    void absentOrderLinesAreAddedAsNotDelivered() {
        List<TestLine> lines = new ArrayList<>(List.of(TestLine.of("L1", "72", "KG")));
        matcher.reconcile(lines, List.of(SALMON, PRAWNS), () -> {
            TestLine added = new TestLine();
            lines.add(added);
            return added;
        });
        assertThat(lines).hasSize(2);
        TestLine added = lines.get(1);
        assertThat(added.poLineId).isEqualTo("L2");
        assertThat(added.description).isEqualTo("Tiger Prawns");
        assertThat(added.discrepancy).isEqualTo(Discrepancy.NOT_DELIVERED);
        assertThat(added.receivedQty).isEqualByComparingTo("0");
        assertThat(added.delta).isEqualByComparingTo("-40");
        assertThat(added.confidence).isNull();
        assertThat(added.needsReview).isFalse();
    }

    @Test
    void reconcileIsRepeatableWithoutAddingLinesTwice() {
        List<TestLine> lines = new ArrayList<>(List.of(TestLine.of("L1", "72", "KG")));
        for (int i = 0; i < 2; i++) {
            matcher.reconcile(lines, List.of(SALMON, PRAWNS), () -> {
                TestLine added = new TestLine();
                lines.add(added);
                return added;
            });
        }
        assertThat(lines).hasSize(2);
    }

    @Test
    void linkOutsideTheOrderIsClearedToUnknownItem() {
        List<TestLine> lines = new ArrayList<>(List.of(TestLine.of("OTHER", "10", "KG")));
        matcher.reconcile(lines, List.of(), TestLine::new);
        assertThat(lines.get(0).poLineId).isNull();
        assertThat(lines.get(0).discrepancy).isEqualTo(Discrepancy.UNKNOWN_ITEM);
    }

    @Test
    void clerkCorrectionRecomputesAndClearsReview() {
        TestLine line = TestLine.of("L1", "72", "KG");
        line.confidence = 0.5;
        classify(line, SALMON);
        assertThat(line.needsReview).isTrue();
        assertThat(line.discrepancy).isEqualTo(Discrepancy.SHORT);

        line.receivedQty = new BigDecimal("80");
        line.corrected = true;
        classify(line, SALMON);
        assertThat(line.discrepancy).isEqualTo(Discrepancy.MATCH);
        assertThat(line.delta).isEqualByComparingTo("0");
        assertThat(line.needsReview).isFalse();
    }

    @Test
    void confidenceAtThresholdIsNotFlagged() {
        TestLine line = TestLine.of("L1", "80", "KG");
        line.confidence = 0.80;
        assertThat(classify(line, SALMON).needsReview).isFalse();
        line.confidence = 0.79;
        assertThat(classify(line, SALMON).needsReview).isTrue();
    }

    @Test
    void receiptIsFlaggedByLineOrPoConfidenceOrMissingPo() {
        TestLine good = classify(TestLine.of("L1", "80", "KG"), SALMON);
        assertThat(matcher.receiptNeedsReview(List.of(good), "PO-1", 0.9)).isFalse();
        assertThat(matcher.receiptNeedsReview(List.of(good), "PO-1", 0.5)).isTrue();
        assertThat(matcher.receiptNeedsReview(List.of(good), null, 0.9)).isTrue();
        TestLine weak = TestLine.of("L1", "80", "KG");
        weak.confidence = 0.1;
        assertThat(matcher.receiptNeedsReview(List.of(weak), "PO-1", 0.9)).isTrue();
    }

    @Test
    void relinksByExactNormalizedDescription() {
        TestLine line = TestLine.of(null, "72", "KG");
        line.description = "  SALMON-fillet ";
        matcher.relinkByDescription(List.of(line), List.of(SALMON, PRAWNS));
        assertThat(line.poLineId).isEqualTo("L1");
    }

    @Test
    void relinksShortenedDescriptionWhenUnambiguous() {
        OrderLine cheddar = new OrderLine("C", "Cheddar, mature", BigDecimal.TEN, "KG");
        TestLine line = TestLine.of(null, "38", "KG");
        line.description = "Cheddar";
        matcher.relinkByDescription(List.of(line), List.of(cheddar, SALMON));
        assertThat(line.poLineId).isEqualTo("C");
    }

    @Test
    void doesNotRelinkAmbiguousOrUnrelatedDescriptions() {
        OrderLine mature = new OrderLine("C1", "Cheddar, mature", BigDecimal.TEN, "KG");
        OrderLine mild = new OrderLine("C2", "Cheddar, mild", BigDecimal.TEN, "KG");
        TestLine ambiguous = TestLine.of(null, "38", "KG");
        ambiguous.description = "Cheddar";
        TestLine unrelated = TestLine.of(null, "10", "KG");
        unrelated.description = "Scallops";
        matcher.relinkByDescription(List.of(ambiguous, unrelated), List.of(mature, mild));
        assertThat(ambiguous.poLineId).isNull();
        assertThat(unrelated.poLineId).isNull();
    }

    @Test
    void eachOrderLineLinksOnlyOnce() {
        TestLine first = TestLine.of(null, "1", "KG");
        first.description = "Salmon fillet";
        TestLine second = TestLine.of(null, "2", "KG");
        second.description = "Salmon fillet";
        matcher.relinkByDescription(List.of(first, second), List.of(SALMON));
        assertThat(first.poLineId).isEqualTo("L1");
        assertThat(second.poLineId).isNull();
    }
}
