package com.dockmind.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {
    @Autowired
    MockMvc mvc;

    @Test
    void documentsEveryApiAndTheUpload() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode docs = new ObjectMapper().readTree(body);
        JsonNode paths = docs.get("paths");
        assertThat(paths.has("/purchase-orders")).isTrue();
        assertThat(paths.has("/receipts")).isTrue();
        assertThat(paths.has("/receipts/{id}")).isTrue();
        assertThat(paths.has("/receipts/{id}/lines/{lineId}")).isTrue();
        assertThat(paths.has("/receipts/{id}/confirm")).isTrue();
        assertThat(paths.has("/receipts/{id}/dispute")).isTrue();
        assertThat(paths.has("/receipts/{id}/image")).isTrue();
        assertThat(paths.at("/~1receipts/post/requestBody/content").has("multipart/form-data")).isTrue();
        assertThat(body).contains("\"format\":\"binary\"");
        assertThat(docs.at("/servers/0/url").asText()).isEqualTo("/");
    }
}
