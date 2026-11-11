package com.dockmind.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.dockmind.ai.Extractor.OpenPo;
import com.dockmind.ai.Extractor.OpenPoLine;
import com.dockmind.domain.PoNumbers;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExtractionValidatorTest {
    private final ExtractionValidator validator = new ExtractionValidator();

    private static final List<OpenPo> POS = List.of(
            new OpenPo("PO-4502", "Blue Ocean Seafood Co.", List.of(
                    new OpenPoLine("POL-4502-1", "Salmon fillet", new BigDecimal("80"), "KG"),
                    new OpenPoLine("POL-4502-2", "Tiger Prawns", new BigDecimal("40"), "KG"))),
            new OpenPo("PO-4503", "Dairy Direct", List.of(
                    new OpenPoLine("POL-4503-1", "Milk 1L", new BigDecimal("240"), "EA"))),
            new OpenPo("PO-4513", "Green Valley Eggs", List.of(
                    new OpenPoLine("POL-4513-1", "Eggs, large, tray", new BigDecimal("120"), "TRAY"))));

    /** Stands in for the indexed database lookup, with the same number normalisation. */
    private static final PoLookup LOOKUP = written -> POS.stream()
            .filter(po -> PoNumbers.lookupKeys(written).contains(po.poNumber()))
            .findFirst();

    private static String answer(String po, String supplier, String lines) {
        return """
                {"supplierName": %s, "deliveryNoteNo": "DN-1", "poNumber": %s, "poConfidence": 0.9,
                 "lines": %s, "freeText": null, "overallConfidence": 0.9, "aiNote": "note"}
                """.formatted(supplier, po, lines);
    }

    private static final String GOOD_LINE =
            "[{\"description\":\"Salmon fillet\",\"poLineId\":\"POL-4502-1\",\"qty\":72,\"unit\":\"KG\",\"confidence\":0.95}]";
    private static final String GOOD = answer("\"PO-4502\"", "\"Blue Ocean Seafood Co.\"", GOOD_LINE);

    private ValidatedExtraction lineAnswer(String line) {
        return validator.validate(answer("\"PO-4502\"", "\"Blue Ocean Seafood Co.\"", "[" + line + "]"), LOOKUP);
    }

    @Test
    void acceptsAGoodAnswer() {
        ValidatedExtraction result = validator.validate(GOOD, LOOKUP);
        assertThat(result.manual()).isFalse();
        assertThat(result.poNumber()).isEqualTo("PO-4502");
        assertThat(result.lines().get(0).poLineId()).isEqualTo("POL-4502-1");
        assertThat(result.lines().get(0).qty()).isEqualByComparingTo("72");
    }

    @Test
    void stripsMarkdownFences() {
        assertThat(validator.validate("```json\n" + GOOD + "\n```", LOOKUP).manual()).isFalse();
    }

    @Test
    void stripsChatterAroundTheJson() {
        assertThat(validator.validate("Here you go:\n" + GOOD + "\nHope that helps!", LOOKUP).manual()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "Sorry, I cannot read this.", "{not json", "[1,2,3]", "{}", "{\"lines\":\"none\"}", "{\"lines\":[]}", "null"})
    void unusableAnswersEndManual(String raw) {
        ValidatedExtraction result = validator.validate(raw, LOOKUP);
        assertThat(result.manual()).isTrue();
        assertThat(result.poNumber()).isNull();
        assertThat(result.aiNote()).isEqualTo(ExtractionValidator.UNREADABLE_NOTE);
    }

    @Test
    void nullAnswerEndsManual() {
        assertThat(validator.validate(null, LOOKUP).manual()).isTrue();
    }

    @Test
    void coercesStringsToNumbers() {
        ValidatedExtraction result = lineAnswer(
                "{\"description\":\"Salmon\",\"poLineId\":\"POL-4502-1\",\"qty\":\"1,072.5 kg\",\"unit\":\"KG\",\"confidence\":\"0.95\"}");
        assertThat(result.manual()).isFalse();
        assertThat(result.lines().get(0).qty()).isEqualByComparingTo("1072.5");
        assertThat(result.lines().get(0).confidence()).isEqualTo(0.95);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"abc\"", "\"about 72\"", "-5", "1e30", "1e999", "true", "null", "[72]", "\"1,5\""})
    void unreadableQuantityEndsManualWithZeroConfidence(String qty) {
        ValidatedExtraction result = lineAnswer(
                "{\"description\":\"Salmon\",\"poLineId\":\"POL-4502-1\",\"qty\":" + qty + ",\"unit\":\"KG\",\"confidence\":0.95}");
        assertThat(result.manual()).isTrue();
        assertThat(result.poNumber()).isNull();
        assertThat(result.lines().get(0).qty()).isNull();
        assertThat(result.lines().get(0).confidence()).isZero();
        assertThat(result.lines().get(0).poLineId()).isNull();
    }

    @Test
    void missingDescriptionEndsManual() {
        ValidatedExtraction result = lineAnswer("{\"poLineId\":null,\"qty\":1,\"unit\":\"KG\",\"confidence\":0.9}");
        assertThat(result.manual()).isTrue();
        assertThat(result.lines().get(0).description()).isEqualTo(ExtractionValidator.UNREADABLE_DESCRIPTION);
    }

    @Test
    void clampsConfidence() {
        assertThat(lineAnswer("{\"description\":\"a\",\"qty\":1,\"unit\":\"KG\",\"confidence\":7}").lines().get(0).confidence()).isEqualTo(1.0);
        assertThat(lineAnswer("{\"description\":\"a\",\"qty\":1,\"unit\":\"KG\",\"confidence\":-2}").lines().get(0).confidence()).isEqualTo(0.0);
        assertThat(lineAnswer("{\"description\":\"a\",\"qty\":1,\"unit\":\"KG\",\"confidence\":\"95%\"}").lines().get(0).confidence()).isEqualTo(0.95);
    }

    @Test
    void missingConfidenceCountsAsZero() {
        assertThat(lineAnswer("{\"description\":\"a\",\"qty\":1,\"unit\":\"KG\"}").lines().get(0).confidence()).isZero();
    }

    @Test
    void unknownPoNumberEndsManual() {
        ValidatedExtraction result = validator.validate(answer("\"PO-9999\"", "\"Blue Ocean Seafood Co.\"", GOOD_LINE), LOOKUP);
        assertThat(result.manual()).isTrue();
        assertThat(result.poNumber()).isNull();
        assertThat(result.lines().get(0).poLineId()).isNull();
    }

    @Test
    void nullPoNumberEndsManual() {
        assertThat(validator.validate(answer("null", "\"Blue Ocean Seafood Co.\"", GOOD_LINE), LOOKUP).manual()).isTrue();
    }

    @Test
    void supplierDisagreementEndsManualAndClearsLinks() {
        String raw = answer("\"PO-4513\"", "\"Dairy Direct\"",
                "[{\"description\":\"Milk 1L\",\"poLineId\":\"POL-4503-1\",\"qty\":252,\"unit\":\"EA\",\"confidence\":0.7}]");
        ValidatedExtraction result = validator.validate(raw, LOOKUP);
        assertThat(result.manual()).isTrue();
        assertThat(result.poNumber()).isNull();
        assertThat(result.supplier()).isEqualTo("Dairy Direct");
        assertThat(result.poConfidence()).isEqualTo(0.9);
        assertThat(result.lines().get(0).poLineId()).isNull();
        assertThat(result.lines().get(0).qty()).isEqualByComparingTo("252");
    }

    @Test
    void missingSupplierEndsManual() {
        assertThat(validator.validate(answer("\"PO-4502\"", "null", GOOD_LINE), LOOKUP).manual()).isTrue();
    }

    @Test
    void supplierComparisonIgnoresCaseAndCompanySuffix() {
        ValidatedExtraction result = validator.validate(answer("\"po 4502\"", "\"BLUE OCEAN SEAFOOD\"", GOOD_LINE), LOOKUP);
        assertThat(result.manual()).isFalse();
        assertThat(result.poNumber()).isEqualTo("PO-4502");
        assertThat(result.supplier()).isEqualTo("Blue Ocean Seafood Co.");
    }

    @Test
    void poLineFromAnotherPoIsCleared() {
        ValidatedExtraction result = lineAnswer("{\"description\":\"Milk\",\"poLineId\":\"POL-4503-1\",\"qty\":1,\"unit\":\"EA\",\"confidence\":0.9}");
        assertThat(result.manual()).isFalse();
        assertThat(result.lines().get(0).poLineId()).isNull();
    }

    @Test
    void nonexistentPoLineIsCleared() {
        ValidatedExtraction result = lineAnswer("{\"description\":\"X\",\"poLineId\":\"POL-NOPE\",\"qty\":1,\"unit\":\"KG\",\"confidence\":0.9}");
        assertThat(result.lines().get(0).poLineId()).isNull();
    }

    @Test
    void secondClaimOnTheSamePoLineIsCleared() {
        String line = "{\"description\":\"Salmon\",\"poLineId\":\"POL-4502-1\",\"qty\":1,\"unit\":\"KG\",\"confidence\":0.9}";
        ValidatedExtraction result = lineAnswer(line + "," + line);
        assertThat(result.lines().get(0).poLineId()).isEqualTo("POL-4502-1");
        assertThat(result.lines().get(1).poLineId()).isNull();
    }

    @Test
    void overlongTextIsTruncated() {
        String longNote = "x".repeat(5000);
        ValidatedExtraction result = validator.validate(GOOD.replace("\"note\"", "\"" + longNote + "\""), LOOKUP);
        assertThat(result.aiNote()).hasSize(2000);
    }
}
