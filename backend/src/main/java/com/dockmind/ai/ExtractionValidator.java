package com.dockmind.ai;

import com.dockmind.ai.Extractor.OpenPo;
import com.dockmind.ai.Extractor.OpenPoLine;
import com.dockmind.domain.Receipt;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Treats every reader's answer as untrusted input. Whatever comes in, the result is either a clean extraction or
 * one marked manual. It never throws, so a bad answer cannot become a 500 or a lost receipt.
 */
@Component
public class ExtractionValidator {
    public static final String UNREADABLE_NOTE =
            "This note could not be read automatically. Please choose the PO and enter the quantities.";
    static final String UNRESOLVED_PO_NOTE = "The PO could not be confirmed from this note. Please choose it.";
    static final String UNREADABLE_LINE_NOTE =
            "A line on this note could not be read. Please choose the PO and check the quantities.";
    static final String UNREADABLE_DESCRIPTION = "Unreadable line";

    private static final ObjectMapper JSON =
            new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final Pattern FENCED = Pattern.compile("```[a-zA-Z]*\\s*(.*?)```", Pattern.DOTALL);
    private static final Pattern QUANTITY =
            Pattern.compile("^([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(\\.[0-9]+)?\\s*[a-zA-Z]*$");
    private static final BigDecimal MAX_QTY = new BigDecimal("1000000000");
    private static final Set<String> SUPPLIER_SUFFIXES =
            Set.of("ltd", "limited", "co", "company", "inc", "llc", "plc", "corp");

    public ValidatedExtraction validate(String raw, List<OpenPo> openPos) {
        try {
            return parse(raw, openPos);
        } catch (JsonProcessingException | RuntimeException e) {
            return ValidatedExtraction.failed(UNREADABLE_NOTE);
        }
    }

    private ValidatedExtraction parse(String raw, List<OpenPo> openPos) throws JsonProcessingException {
        if (raw == null || raw.isBlank()) {
            return ValidatedExtraction.failed(UNREADABLE_NOTE);
        }
        JsonNode root = JSON.readTree(stripWrapping(raw));
        JsonNode rawLines = root == null ? null : root.get("lines");
        // A note with no lines was not read. Saving it would report the whole PO as not delivered.
        if (root == null || !root.isObject() || rawLines == null || !rawLines.isArray() || rawLines.isEmpty()) {
            return ValidatedExtraction.failed(UNREADABLE_NOTE);
        }

        List<ValidatedExtraction.Line> lines = new ArrayList<>();
        boolean anyUnreadable = false;
        for (JsonNode node : rawLines) {
            ValidatedExtraction.Line line = readLine(node);
            anyUnreadable |= line.qty() == null || UNREADABLE_DESCRIPTION.equals(line.description());
            lines.add(line);
        }

        String supplier = text(root.get("supplierName"), Receipt.NAME_LIMIT);
        String deliveryNoteNo = text(root.get("deliveryNoteNo"), Receipt.NAME_LIMIT);
        Double poConfidence = orZero(confidence(root.get("poConfidence")));
        String freeText = text(root.get("freeText"), Receipt.TEXT_LIMIT);
        Double overall = confidence(root.get("overallConfidence"));
        String aiNote = text(root.get("aiNote"), Receipt.TEXT_LIMIT);
        OpenPo po = resolve(text(root.get("poNumber"), Receipt.NAME_LIMIT), supplier, openPos);

        if (po == null || anyUnreadable) {
            String note = anyUnreadable ? UNREADABLE_LINE_NOTE : aiNote != null ? aiNote : UNRESOLVED_PO_NOTE;
            return new ValidatedExtraction(true, null, supplier, deliveryNoteNo, poConfidence, freeText, overall, note,
                    clearLinks(lines));
        }
        return new ValidatedExtraction(false, po.poNumber(), po.supplier(), deliveryNoteNo, poConfidence, freeText,
                overall, aiNote, keepValidLinks(lines, po));
    }

    /** Removes markdown fences, or any chatter around the JSON object. */
    private static String stripWrapping(String raw) {
        Matcher fenced = FENCED.matcher(raw);
        String body = fenced.find() ? fenced.group(1) : raw;
        int start = body.indexOf('{');
        int end = body.lastIndexOf('}');
        return start >= 0 && end > start ? body.substring(start, end + 1) : body.trim();
    }

    private static ValidatedExtraction.Line readLine(JsonNode node) {
        String description = text(node.get("description"), Receipt.TEXT_LIMIT);
        BigDecimal qty = quantity(node.get("qty"));
        // A missing confidence counts as zero: an answer that gives no confidence has not earned any.
        Double confidence = qty == null ? Double.valueOf(0.0) : orZero(confidence(node.get("confidence")));
        return new ValidatedExtraction.Line(description == null ? UNREADABLE_DESCRIPTION : description,
                text(node.get("poLineId"), Receipt.NAME_LIMIT), qty, text(node.get("unit"), Receipt.NAME_LIMIT),
                confidence);
    }

    /** A PO counts only if the number exists and the note's supplier agrees. PO-4503 and PO-4513 differ by one digit. */
    private static OpenPo resolve(String poNumber, String supplier, List<OpenPo> openPos) {
        if (poNumber == null || supplier == null) {
            return null;
        }
        String wanted = alphanumeric(poNumber);
        return openPos.stream()
                .filter(po -> alphanumeric(po.poNumber()).equals(wanted))
                .filter(po -> supplierWords(po.supplier()).equals(supplierWords(supplier)))
                .findFirst()
                .orElse(null);
    }

    private static List<ValidatedExtraction.Line> clearLinks(List<ValidatedExtraction.Line> lines) {
        return lines.stream().map(line -> withLink(line, null)).toList();
    }

    private static List<ValidatedExtraction.Line> keepValidLinks(List<ValidatedExtraction.Line> lines, OpenPo po) {
        Set<String> available = po.lines().stream().map(OpenPoLine::id).collect(Collectors.toCollection(HashSet::new));
        // Removing on first use also clears a second line that claims the same PO line.
        return lines.stream()
                .map(line -> withLink(line, available.remove(line.poLineId()) ? line.poLineId() : null))
                .toList();
    }

    private static ValidatedExtraction.Line withLink(ValidatedExtraction.Line line, String poLineId) {
        return new ValidatedExtraction.Line(line.description(), poLineId, line.qty(), line.unit(), line.confidence());
    }

    private static String text(JsonNode node, int limit) {
        if (node == null || !(node.isTextual() || node.isNumber())) {
            return null;
        }
        String value = node.asText().trim();
        if (value.isEmpty() || value.equalsIgnoreCase("null")) {
            return null;
        }
        return value.length() > limit ? value.substring(0, limit) : value;
    }

    private static BigDecimal quantity(JsonNode node) {
        BigDecimal value = number(node);
        if (value == null || value.signum() < 0 || value.compareTo(MAX_QTY) > 0) {
            return null;
        }
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal number(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (!node.isTextual()) {
            return null;
        }
        Matcher found = QUANTITY.matcher(node.asText().trim());
        if (!found.matches()) {
            return null;
        }
        return new BigDecimal(found.group(1).replace(",", "") + (found.group(2) == null ? "" : found.group(2)));
    }

    private static Double confidence(JsonNode node) {
        if (node == null) {
            return null;
        }
        BigDecimal value;
        if (node.isTextual() && node.asText().trim().endsWith("%")) {
            String percent = node.asText().trim().replace("%", "").trim();
            try {
                value = new BigDecimal(percent).movePointLeft(2);
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            value = number(node);
        }
        return value == null ? null : Math.max(0.0, Math.min(1.0, value.doubleValue()));
    }

    private static Double orZero(Double value) {
        return value == null ? Double.valueOf(0.0) : value;
    }

    private static String alphanumeric(String value) {
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static List<String> supplierWords(String supplier) {
        return Arrays.stream(supplier.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().split(" "))
                .filter(word -> !SUPPLIER_SUFFIXES.contains(word))
                .toList();
    }
}
