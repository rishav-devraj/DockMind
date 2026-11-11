package com.dockmind.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dockmind.ai.Extractor.ExtractionRequest;
import com.dockmind.ai.Extractor.OpenPo;
import com.dockmind.ai.Extractor.OpenPoLine;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LlmExtractorTest {
    private static final OpenPo PO = new OpenPo("PO-4502", "Blue Ocean Seafood Co.", List.of(
            new OpenPoLine("POL-4502-1", "Salmon fillet", new BigDecimal("80"), "KG")));
    private static final PoLookup LOOKUP = written -> "PO-4502".equals(written) ? Optional.of(PO) : Optional.empty();
    private static final byte[] IMAGE = {1, 2, 3};
    private static final String GOOD = """
            {"supplierName":"Blue Ocean Seafood Co.","deliveryNoteNo":"DN-1","poNumber":"PO-4502","poConfidence":0.9,
             "lines":[{"description":"Salmon fillet","qty":72,"unit":"KG","confidence":0.9}],
             "freeText":null,"overallConfidence":0.9,"aiNote":"Clear."}
            """;

    static class StubGateway extends LlmGateway {
        final String answer;
        String prompt;
        byte[] image;
        String mimeType;

        StubGateway(String answer) {
            this.answer = answer;
        }

        @Override
        public String callVision(String prompt, byte[] image, String mimeType) throws Exception {
            this.prompt = prompt;
            this.image = image;
            this.mimeType = mimeType;
            if (answer == null) {
                throw new IllegalStateException("LLM HTTP 503");
            }
            return answer;
        }
    }

    private final ExtractionValidator validator = new ExtractionValidator();

    private static ExtractionRequest request() {
        return new ExtractionRequest(IMAGE, "image/png", "photo.png", "note-3");
    }

    private ValidatedExtraction run(String answer) throws Exception {
        return validator.validate(new LlmExtractor(new StubGateway(answer)).extract(request()), LOOKUP);
    }

    @Test
    void sendsThePhotoAndAsksForTheShapeWithoutAnyPurchaseOrders() throws Exception {
        StubGateway gateway = new StubGateway(GOOD);
        new LlmExtractor(gateway).extract(request());
        assertThat(gateway.image).isEqualTo(IMAGE);
        assertThat(gateway.mimeType).isEqualTo("image/png");
        assertThat(gateway.prompt).contains("supplierName", "poNumber", "poConfidence", "overallConfidence", "aiNote");
        assertThat(gateway.prompt).doesNotContain("PO-45", "POL-", "poLineId");
        assertThat(gateway.prompt).contains("Never guess a missing digit");
    }

    @Test
    void promptIsTheSameWhateverTheNumberOfPurchaseOrders() {
        assertThat(LlmExtractor.PROMPT.length()).isLessThan(3000);
    }

    @Test
    void goodAnswerIsAccepted() throws Exception {
        ValidatedExtraction result = run(GOOD);
        assertThat(result.manual()).isFalse();
        assertThat(result.poNumber()).isEqualTo("PO-4502");
    }

    @Test
    void fencedAnswerIsAccepted() throws Exception {
        assertThat(run("```json\n" + GOOD + "```").manual()).isFalse();
    }

    @Test
    void garbageAnswerEndsManual() throws Exception {
        assertThat(run("I am unable to help with that.").manual()).isTrue();
    }

    @Test
    void emptyAnswerEndsManual() throws Exception {
        assertThat(run("").manual()).isTrue();
    }

    @Test
    void gatewayFailurePropagatesToTheRunner() {
        assertThatThrownBy(() -> new LlmExtractor(new StubGateway(null)).extract(request()))
                .hasMessageContaining("503");
    }
}
