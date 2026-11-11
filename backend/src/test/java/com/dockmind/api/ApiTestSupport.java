package com.dockmind.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

abstract class ApiTestSupport {
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    @Autowired
    MockMvc mvc;

    final ObjectMapper json = new ObjectMapper();

    static MockMultipartFile png() {
        return new MockMultipartFile("image", "photo.png", "image/png", PNG);
    }

    ResultActions upload(String key, String fixture) throws Exception {
        return mvc.perform(multipart("/receipts").file(png()).header("Idempotency-Key", key).header("X-Fixture", fixture));
    }

    /** Uploads a fixture and waits for extraction to finish. */
    JsonNode captured(String fixture) throws Exception {
        String body = upload(UUID.randomUUID().toString(), fixture).andReturn().getResponse().getContentAsString();
        return poll(json.readTree(body).get("id").asText());
    }

    JsonNode poll(String id) throws Exception {
        for (int i = 0; i < 200; i++) {
            JsonNode receipt = read(id);
            if (!"CAPTURED".equals(receipt.get("status").asText())) {
                return receipt;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Receipt " + id + " stayed in CAPTURED");
    }

    JsonNode read(String id) throws Exception {
        return json.readTree(mvc.perform(get("/receipts/" + id)).andReturn().getResponse().getContentAsString());
    }

    static JsonNode line(JsonNode receipt, String description) {
        for (JsonNode line : receipt.get("lines")) {
            if (description.equals(line.get("description").asText())) {
                return line;
            }
        }
        throw new AssertionError("No line " + description + " in " + receipt);
    }
}
