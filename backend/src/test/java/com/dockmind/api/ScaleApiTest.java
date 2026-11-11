package com.dockmind.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dockmind.ai.LlmGateway;
import com.dockmind.service.ReceiptService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/**
 * The read-first pipeline with the real wiring and a stubbed model: the prompt carries no purchase orders, the PO is
 * found by an indexed lookup, and lines are linked to it in code. Also covers paging on A1 and A2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"extractor.type=llm", "spring.datasource.url=jdbc:h2:mem:dockmind-scale;DB_CLOSE_DELAY=-1"})
class ScaleApiTest extends ApiTestSupport {
    static final AtomicReference<String> LAST_PROMPT = new AtomicReference<>();

    /** What a model reading note 2 returns now that it is never told about PO lines. */
    static final String NOTE_2 = """
            {"supplierName":"Blue Ocean Seafood Co.","deliveryNoteNo":"DN-30417","poNumber":"po 4502","poConfidence":0.97,
             "lines":[{"description":"Salmon fillet","qty":72,"unit":"KG","confidence":0.95},
                      {"description":"Scallops","qty":10,"unit":"KG","confidence":0.93}],
             "freeText":null,"overallConfidence":0.94,"aiNote":"Clear."}
            """;

    @TestConfiguration
    static class StubModel {
        @Bean
        @Primary
        LlmGateway stubLlmGateway() {
            return new LlmGateway() {
                @Override
                public String callVision(String prompt, byte[] image, String mimeType) {
                    LAST_PROMPT.set(prompt);
                    return NOTE_2;
                }
            };
        }
    }

    @Autowired
    ReceiptService service;

    @Test
    void readFirstThenLookUpThenLinkInCode() throws Exception {
        JsonNode receipt = captured("ignored");
        assertThat(LAST_PROMPT.get()).doesNotContain("PO-4501", "PO-4502", "POL-");
        assertThat(receipt.get("status").asText()).isEqualTo("EXTRACTED");
        assertThat(receipt.get("poNumber").asText()).isEqualTo("PO-4502");
        assertThat(line(receipt, "Salmon fillet").get("poLineId").asText()).isEqualTo("POL-4502-1");
        assertThat(line(receipt, "Salmon fillet").get("discrepancy").asText()).isEqualTo("SHORT");
        assertThat(line(receipt, "Scallops").get("discrepancy").asText()).isEqualTo("UNKNOWN_ITEM");
        assertThat(line(receipt, "Tiger Prawns").get("discrepancy").asText()).isEqualTo("NOT_DELIVERED");
    }

    @Test
    void openPoLookupNormalisesTheWrittenNumber() {
        assertThat(service.findOpenPo("PO 4513")).get().extracting(po -> po.supplier()).isEqualTo("Green Valley Eggs");
        assertThat(service.findOpenPo("4502")).isPresent();
        assertThat(service.findOpenPo("PO-9999")).isEmpty();
        assertThat(service.findOpenPo(null)).isEmpty();
    }

    @Test
    void purchaseOrdersArePagedWithATotal() throws Exception {
        mvc.perform(get("/purchase-orders").param("status", "OPEN").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "4"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value("PO-4501"));
        mvc.perform(get("/purchase-orders").param("status", "OPEN").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$[0].id").value("PO-4503"));
    }

    @Test
    void purchaseOrdersCanBeSearchedByNumberOrSupplier() throws Exception {
        mvc.perform(get("/purchase-orders").param("q", "dairy"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].poNumber").value("PO-4503"));
        mvc.perform(get("/purchase-orders").param("q", "4513"))
                .andExpect(jsonPath("$[0].supplier").value("Green Valley Eggs"));
    }

    @Test
    void receiptsArePagedNewestFirst() throws Exception {
        captured("a");
        captured("b");
        mvc.perform(get("/receipts").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(header().exists("X-Total-Count"));
    }

    @Test
    void invalidPagingIs400() throws Exception {
        mvc.perform(get("/receipts").param("size", "0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PAGE"));
        mvc.perform(get("/purchase-orders").param("size", "500")).andExpect(status().isBadRequest());
        mvc.perform(get("/purchase-orders").param("page", "-1")).andExpect(status().isBadRequest());
    }
}
