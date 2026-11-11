package com.dockmind.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.dockmind.ai.LlmGateway;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/** Runs the real wiring with extractor.type=llm against a gateway that always fails. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"extractor.type=llm", "spring.datasource.url=jdbc:h2:mem:dockmind-llm;DB_CLOSE_DELAY=-1"})
class LlmFailureApiTest extends ApiTestSupport {

    @TestConfiguration
    static class FailingGateway {
        @Bean
        @Primary
        LlmGateway failingLlmGateway() {
            return new LlmGateway() {
                @Override
                public String callVision(String prompt, byte[] image, String mimeType) {
                    throw new IllegalStateException("LLM HTTP 500: boom");
                }
            };
        }
    }

    @Test
    void modelFailureEndsManualWithThePhotoKept() throws Exception {
        JsonNode receipt = captured("note-1");
        assertThat(receipt.get("status").asText()).isEqualTo("NEEDS_MANUAL");
        assertThat(receipt.get("poNumber").isNull()).isTrue();
        assertThat(receipt.get("aiNote").asText()).isNotBlank();
        int status = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/receipts/" + receipt.get("id").asText() + "/image")).andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(200);
    }
}
