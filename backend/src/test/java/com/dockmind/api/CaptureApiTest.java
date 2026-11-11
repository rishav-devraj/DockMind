package com.dockmind.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dockmind.domain.ReceiptRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest
@AutoConfigureMockMvc
class CaptureApiTest extends ApiTestSupport {
    @Autowired
    ReceiptRepository receipts;

    @Test
    void newKeyReturns202CapturedWithNoLines() throws Exception {
        upload(UUID.randomUUID().toString(), "note-1")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("CAPTURED"))
                .andExpect(jsonPath("$.lines").isEmpty())
                .andExpect(jsonPath("$.clerk").value("clerk-01"));
    }

    @Test
    void repeatedKeyReturns200WithTheSameReceipt() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = upload(key, "note-1").andReturn().getResponse().getContentAsString();
        String id = json.readTree(first).get("id").asText();
        poll(id);
        upload(key, "note-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("EXTRACTED"));
    }

    @Test
    void concurrentSameKeyCreatesOneReceipt() throws Exception {
        String key = UUID.randomUUID().toString();
        long before = receipts.count();
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<int[]>> calls = new ArrayList<>();
        List<String> bodies = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < threads; i++) {
            calls.add(() -> {
                var response = upload(key, "note-1").andReturn().getResponse();
                bodies.add(response.getContentAsString());
                return new int[] {response.getStatus()};
            });
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<int[]> result : pool.invokeAll(calls)) {
            statuses.add(result.get()[0]);
        }
        pool.shutdown();
        assertThat(statuses).containsOnly(200, 202);
        assertThat(statuses.stream().filter(s -> s == 202).count()).isEqualTo(1);
        assertThat(receipts.count()).isEqualTo(before + 1);
        Set<String> ids = bodies.stream().map(this::id).collect(Collectors.toSet());
        assertThat(ids).hasSize(1);
    }

    private String id(String body) {
        try {
            return json.readTree(body).get("id").asText();
        } catch (Exception e) {
            throw new AssertionError(body, e);
        }
    }

    @Test
    void missingKeyIs400() throws Exception {
        mvc.perform(multipart("/receipts").file(png()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_HEADER"));
    }

    @Test
    void blankKeyIs400() throws Exception {
        mvc.perform(multipart("/receipts").file(png()).header("Idempotency-Key", "  "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingImageIs400() throws Exception {
        mvc.perform(multipart("/receipts").header("Idempotency-Key", "k-missing"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_IMAGE"));
    }

    @Test
    void nonMultipartRequestIs400() throws Exception {
        mvc.perform(post("/receipts").header("Idempotency-Key", "k-json").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fileThatIsNotAnImageIs400() throws Exception {
        MockMultipartFile text = new MockMultipartFile("image", "a.png", "image/png", "hello".getBytes());
        mvc.perform(multipart("/receipts").file(text).header("Idempotency-Key", "k-text"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_IMAGE"));
    }

    @Test
    void servesTheStoredPhoto() throws Exception {
        JsonNode receipt = captured("note-1");
        mvc.perform(get("/receipts/" + receipt.get("id").asText() + "/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PNG));
    }

    @Test
    void unknownPhotoIs404EvenWhenOnlyImagesAreAccepted() throws Exception {
        mvc.perform(get("/receipts/RCP-999999/image").accept("image/png"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RECEIPT_NOT_FOUND"));
    }

    @Test
    void cleanNoteIsAllMatch() throws Exception {
        JsonNode receipt = captured("note-1");
        assertThat(receipt.get("status").asText()).isEqualTo("EXTRACTED");
        assertThat(receipt.get("poNumber").asText()).isEqualTo("PO-4501");
        assertThat(receipt.get("needsReview").asBoolean()).isFalse();
        assertThat(receipt.get("lines")).hasSize(3);
        receipt.get("lines").forEach(l -> assertThat(l.get("discrepancy").asText()).isEqualTo("MATCH"));
    }

    @Test
    void shortNoteGivesThreeDiscrepancies() throws Exception {
        JsonNode receipt = captured("note-2");
        assertThat(line(receipt, "Salmon fillet").get("discrepancy").asText()).isEqualTo("SHORT");
        assertThat(line(receipt, "Salmon fillet").get("delta").asInt()).isEqualTo(-8);
        assertThat(line(receipt, "Scallops").get("discrepancy").asText()).isEqualTo("UNKNOWN_ITEM");
        assertThat(line(receipt, "Scallops").get("orderedQty").isNull()).isTrue();
        JsonNode prawns = line(receipt, "Tiger Prawns");
        assertThat(prawns.get("discrepancy").asText()).isEqualTo("NOT_DELIVERED");
        assertThat(prawns.get("delta").asInt()).isEqualTo(-40);
        assertThat(prawns.get("aiQty").isNull()).isTrue();
        assertThat(prawns.get("confidence").isNull()).isTrue();
    }

    @Test
    void messyNoteEndsManualWithNoPo() throws Exception {
        JsonNode receipt = captured("note-3");
        assertThat(receipt.get("status").asText()).isEqualTo("NEEDS_MANUAL");
        assertThat(receipt.get("poNumber").isNull()).isTrue();
        assertThat(receipt.get("supplier").asText()).isEqualTo("Dairy Direct");
        assertThat(receipt.get("needsReview").asBoolean()).isTrue();
        assertThat(receipt.get("lines")).hasSize(3);
        receipt.get("lines").forEach(l -> {
            assertThat(l.get("discrepancy").asText()).isEqualTo("UNKNOWN_ITEM");
            assertThat(l.get("poLineId").isNull()).isTrue();
        });
    }

    @Test
    void garbageEndsManualNotAnError() throws Exception {
        JsonNode receipt = captured("garbage");
        assertThat(receipt.get("status").asText()).isEqualTo("NEEDS_MANUAL");
        assertThat(receipt.get("lines")).isEmpty();
        assertThat(receipt.get("aiNote").asText()).isNotBlank();
    }

    @Test
    void wholeNumbersRenderWithoutDecimals() throws Exception {
        JsonNode receipt = captured("note-2");
        assertThat(line(receipt, "Salmon fillet").get("orderedQty").toString()).isEqualTo("80");
    }
}
