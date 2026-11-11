package com.dockmind.matching;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * All discrepancy logic lives here, in plain code with no Spring or database dependency.
 * The model only reads the note; this class decides what is wrong with it.
 */
public final class Matcher {

    /** The fields of a receipt line that matching reads and writes. */
    public interface Line {
        String getPoLineId();

        void setPoLineId(String poLineId);

        String getDescription();

        void setDescription(String description);

        BigDecimal getOrderedQty();

        void setOrderedQty(BigDecimal orderedQty);

        BigDecimal getReceivedQty();

        void setReceivedQty(BigDecimal receivedQty);

        String getUnit();

        void setUnit(String unit);

        Double getConfidence();

        boolean isCorrectedByClerk();

        void setDiscrepancy(Discrepancy discrepancy);

        void setDelta(BigDecimal delta);

        void setNeedsReview(boolean needsReview);
    }

    public record OrderLine(String id, String description, BigDecimal orderedQty, String unit) {
    }

    private static final Map<String, String> UNIT_ALIASES = Map.ofEntries(
            Map.entry("KGS", "KG"), Map.entry("KILO", "KG"), Map.entry("KILOS", "KG"),
            Map.entry("KILOGRAM", "KG"), Map.entry("KILOGRAMS", "KG"),
            Map.entry("CASES", "CASE"),
            Map.entry("EACH", "EA"), Map.entry("PCS", "EA"), Map.entry("PC", "EA"),
            Map.entry("PIECE", "EA"), Map.entry("PIECES", "EA"),
            Map.entry("TRAYS", "TRAY"));

    private final double reviewThreshold;

    public Matcher(double reviewThreshold) {
        this.reviewThreshold = reviewThreshold;
    }

    public static String normalizeUnit(String unit) {
        if (unit == null) {
            return null;
        }
        String cleaned = unit.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        return UNIT_ALIASES.getOrDefault(cleaned, cleaned);
    }

    public static String normalizeDescription(String description) {
        if (description == null) {
            return "";
        }
        return description.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /**
     * Classifies every line against the order and adds a NOT_DELIVERED line for each order line absent from the note.
     * Pass an empty order when no PO is resolved: every line then becomes UNKNOWN_ITEM.
     */
    public <L extends Line> void reconcile(List<L> lines, List<OrderLine> order, Supplier<L> newLine) {
        Map<String, OrderLine> byId = order.stream().collect(Collectors.toMap(OrderLine::id, Function.identity()));
        Set<String> present = new HashSet<>();
        for (L line : lines) {
            OrderLine linked = line.getPoLineId() == null ? null : byId.get(line.getPoLineId());
            if (linked == null) {
                line.setPoLineId(null);
            } else {
                present.add(linked.id());
            }
            classify(line, linked);
        }
        for (OrderLine orderLine : order) {
            if (!present.contains(orderLine.id())) {
                L added = newLine.get();
                added.setPoLineId(orderLine.id());
                added.setDescription(orderLine.description());
                added.setReceivedQty(BigDecimal.ZERO);
                added.setUnit(orderLine.unit());
                classify(added, orderLine);
            }
        }
    }

    /** Applies the contract's rules, in order, to one line. {@code linked} is null when no PO line is linked. */
    public void classify(Line line, OrderLine linked) {
        String unit = normalizeUnit(line.getUnit());
        line.setUnit(unit);
        line.setNeedsReview(lineNeedsReview(line));
        if (linked == null) {
            line.setOrderedQty(null);
            line.setDiscrepancy(Discrepancy.UNKNOWN_ITEM);
            line.setDelta(null);
            return;
        }
        BigDecimal ordered = linked.orderedQty();
        BigDecimal received = line.getReceivedQty();
        line.setOrderedQty(ordered);
        if (unit == null || !unit.equals(normalizeUnit(linked.unit()))) {
            line.setDiscrepancy(Discrepancy.UNIT_MISMATCH);
            line.setDelta(null);
            return;
        }
        line.setDelta(received.subtract(ordered));
        if (received.signum() == 0) {
            line.setDiscrepancy(Discrepancy.NOT_DELIVERED);
            return;
        }
        int comparison = received.compareTo(ordered);
        line.setDiscrepancy(comparison < 0 ? Discrepancy.SHORT : comparison > 0 ? Discrepancy.OVER : Discrepancy.MATCH);
    }

    /**
     * Links lines to order lines by description. An exact normalized match is tried first, then a match where one
     * description's words all appear in the other, accepted only when exactly one order line fits. A note often
     * shortens a name ("Cheddar" for "Cheddar, mature"). Each order line is linked at most once.
     */
    public <L extends Line> void relinkByDescription(List<L> lines, List<OrderLine> order) {
        Set<String> taken = new HashSet<>();
        lines.forEach(line -> line.setPoLineId(null));
        for (L line : lines) {
            String description = normalizeDescription(line.getDescription());
            order.stream()
                    .filter(o -> !taken.contains(o.id()))
                    .filter(o -> !description.isEmpty() && description.equals(normalizeDescription(o.description())))
                    .findFirst()
                    .ifPresent(o -> link(line, o, taken));
        }
        for (L line : lines) {
            if (line.getPoLineId() != null) {
                continue;
            }
            Set<String> words = words(line.getDescription());
            List<OrderLine> candidates = order.stream()
                    .filter(o -> !taken.contains(o.id()))
                    .filter(o -> overlapsCompletely(words, words(o.description())))
                    .toList();
            if (candidates.size() == 1) {
                link(line, candidates.get(0), taken);
            }
        }
    }

    public boolean receiptNeedsReview(List<? extends Line> lines, String poNumber, Double poConfidence) {
        return poNumber == null
                || (poConfidence != null && poConfidence < reviewThreshold)
                || lines.stream().anyMatch(this::lineNeedsReview);
    }

    private boolean lineNeedsReview(Line line) {
        return !line.isCorrectedByClerk() && line.getConfidence() != null && line.getConfidence() < reviewThreshold;
    }

    private static void link(Line line, OrderLine orderLine, Set<String> taken) {
        line.setPoLineId(orderLine.id());
        taken.add(orderLine.id());
    }

    private static Set<String> words(String description) {
        String normalized = normalizeDescription(description);
        if (normalized.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(normalized.split(" ")));
    }

    private static boolean overlapsCompletely(Set<String> a, Set<String> b) {
        return !a.isEmpty() && !b.isEmpty() && (a.containsAll(b) || b.containsAll(a));
    }
}
