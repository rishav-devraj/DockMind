package com.dockmind.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dockmind.ai.Extractor.ExtractionRequest;
import com.dockmind.ai.Extractor.OpenPo;
import com.dockmind.ai.Extractor.OpenPoLine;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmExtractorTest {
    private static final List<OpenPo> POS = List.of(new OpenPo("PO-4502", "Blue Ocean Seafood Co.", List.of(
            new OpenPoLine("POL-4502-1", "Salmon fillet", new BigDecimal("80"), "KG"))));
    private static final byte[] IMAGE = {1, 2, 3};
    private static final String GOOD = """
            {"supplierName":"Blue Ocean Seafood Co.","deliveryNoteNo":"DN-1","poNumber":"PO-4502","poConfidence":0.9,
             "lines":[{"description":"Salmon fillet","poLineId":"POL-4502-1","qty":72,"unit":"KG","confidence":0.9}],
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
        return new ExtractionRequest(IMAGE, "image/png", "photo.png", "note-3", POS);
    }

    private ValidatedExtraction run(String answer) throws Exception {
        return validator.validate(new LlmExtractor(new StubGateway(answer)).extract(request()), POS);
    }

    @Test
    void sendsThePhotoAndOpenPosAndAsksForTheShape() throws Exception {
        StubGateway gateway = new StubGateway(GOOD);
        new LlmExtractor(gateway).extract(request());
        assertThat(gateway.image).isEqualTo(IMAGE);
        assertThat(gateway.mimeType).isEqualTo("image/png");
        assertThat(gateway.prompt).contains("PO-4502", "POL-4502-1", "Salmon fillet", "Blue Ocean Seafood Co.");
        assertThat(gateway.prompt).contains("supplierName", "poLineId", "poConfidence", "overallConfidence", "aiNote");
        assertThat(gateway.prompt).contains("Do not calculate differences");
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
