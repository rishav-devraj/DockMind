package com.dockmind.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dockmind.ai.ValidatedExtraction;
import com.dockmind.domain.Receipt;
import com.dockmind.domain.ReceiptRepository;
import com.dockmind.service.ReceiptService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class DecideApiTest extends ApiTestSupport {
    @Autowired
    ReceiptRepository receipts;

    @Autowired
    ReceiptService service;

    private static String id(JsonNode receipt) {
        return receipt.get("id").asText();
    }

    private ResultActions correct(JsonNode receipt, JsonNode line, String body) throws Exception {
        return mvc.perform(patch("/receipts/" + id(receipt) + "/lines/" + line.get("id").asText())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions confirm(String id) throws Exception {
        return mvc.perform(post("/receipts/" + id + "/confirm"));
    }

    private ResultActions dispute(String id, String body) throws Exception {
        return mvc.perform(post("/receipts/" + id + "/dispute").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions assign(String id, String po) throws Exception {
        return mvc.perform(patch("/receipts/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"poNumber\":\"" + po + "\"}"));
    }

    @Test
    void correctionRecomputesAndMarksTheLine() throws Exception {
        JsonNode receipt = captured("note-2");
        String body = correct(receipt, line(receipt, "Salmon fillet"), "{\"receivedQty\":80}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode salmon = line(json.readTree(body), "Salmon fillet");
        assertThat(salmon.get("discrepancy").asText()).isEqualTo("MATCH");
        assertThat(salmon.get("delta").asInt()).isZero();
        assertThat(salmon.get("correctedByClerk").asBoolean()).isTrue();
        assertThat(salmon.get("aiQty").asInt()).isEqualTo(72);
        assertThat(salmon.get("receivedQty").asInt()).isEqualTo(80);
        assertThat(json.readTree(body).get("lines")).hasSize(3);
    }

    @Test
    void correctionCanChangeTheUnit() throws Exception {
        JsonNode receipt = captured("note-3");
        assign(id(receipt), "PO-4503").andExpect(status().isOk());
        receipt = read(id(receipt));
        String body = correct(receipt, line(receipt, "Butter 500g"), "{\"receivedQty\":100,\"unit\":\"each\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode butter = line(json.readTree(body), "Butter 500g");
        assertThat(butter.get("unit").asText()).isEqualTo("EA");
        assertThat(butter.get("discrepancy").asText()).isEqualTo("MATCH");
    }

    @Test
    void invalidCorrectionsAre400() throws Exception {
        JsonNode receipt = captured("note-2");
        JsonNode salmon = line(receipt, "Salmon fillet");
        correct(receipt, salmon, "{}").andExpect(status().isBadRequest());
        correct(receipt, salmon, "{\"receivedQty\":-1}").andExpect(status().isBadRequest());
        correct(receipt, salmon, "{\"receivedQty\":\"abc\"}").andExpect(status().isBadRequest());
        correct(receipt, salmon, "{\"receivedQty\":1e30}").andExpect(status().isBadRequest());
        correct(receipt, salmon, "{\"receivedQty\":1,\"unit\":\" \"}").andExpect(status().isBadRequest());
        correct(receipt, salmon, "not json").andExpect(status().isBadRequest());
    }

    @Test
    void unknownLineOrReceiptIs404() throws Exception {
        JsonNode receipt = captured("note-2");
        mvc.perform(patch("/receipts/" + id(receipt) + "/lines/RL-999999")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"receivedQty\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("LINE_NOT_FOUND"));
        mvc.perform(patch("/receipts/RCP-999999/lines/RL-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"receivedQty\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RECEIPT_NOT_FOUND"));
    }

    @Test
    void lineFromAnotherReceiptIs404() throws Exception {
        JsonNode first = captured("note-1");
        JsonNode second = captured("note-2");
        correct(first, line(second, "Salmon fillet"), "{\"receivedQty\":1}").andExpect(status().isNotFound());
    }

    @Test
    void confirmIsSafeToRepeat() throws Exception {
        String id = id(captured("note-1"));
        confirm(id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        confirm(id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void disputeIsSafeToRepeatAndKeepsTheFirstReason() throws Exception {
        String id = id(captured("note-2"));
        dispute(id, "{\"reason\":\"short on salmon\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPUTED"))
                .andExpect(jsonPath("$.disputeReason").value("short on salmon"));
        dispute(id, "{\"reason\":\"second try\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.disputeReason").value("short on salmon"));
    }

    @Test
    void blankOrMissingReasonIs400() throws Exception {
        String id = id(captured("note-2"));
        dispute(id, "{\"reason\":\"   \"}").andExpect(status().isBadRequest());
        dispute(id, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void confirmAfterDisputeAndDisputeAfterConfirmAre409() throws Exception {
        String disputed = id(captured("note-2"));
        dispute(disputed, "{\"reason\":\"bad\"}").andExpect(status().isOk());
        confirm(disputed).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ALREADY_DISPUTED"));

        String confirmed = id(captured("note-1"));
        confirm(confirmed).andExpect(status().isOk());
        dispute(confirmed, "{\"reason\":\"bad\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_CONFIRMED"));
    }

    @Test
    void editingATerminalReceiptIs409() throws Exception {
        JsonNode receipt = captured("note-1");
        confirm(id(receipt)).andExpect(status().isOk());
        correct(receipt, receipt.get("lines").get(0), "{\"receivedQty\":1}").andExpect(status().isConflict());
        assign(id(receipt), "PO-4501").andExpect(status().isConflict());
    }

    @Test
    void decidingWhileCapturedIs409() throws Exception {
        Receipt pending = receipts.saveAndFlush(new Receipt(UUID.randomUUID().toString(), "clerk-01", null, null));
        String id = "RCP-" + pending.getId();
        confirm(id).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("NOT_READY"));
        dispute(id, "{\"reason\":\"x\"}").andExpect(status().isConflict());
        assign(id, "PO-4501").andExpect(status().isConflict());
        service.applyExtraction(pending.getId(), ValidatedExtraction.failed("done"));
    }

    @Test
    void confirmingWithNoResolvedPoIs409ButDisputeWorks() throws Exception {
        String id = id(captured("garbage"));
        confirm(id).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PO_UNRESOLVED"));
        dispute(id, "{\"reason\":\"unreadable note\"}").andExpect(status().isOk());
    }

    @Test
    void assigningThePoResolvesTheMessyNote() throws Exception {
        String id = id(captured("note-3"));
        String body = assign(id, "PO-4503").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXTRACTED"))
                .andExpect(jsonPath("$.poNumber").value("PO-4503"))
                .andReturn().getResponse().getContentAsString();
        JsonNode receipt = json.readTree(body);
        assertThat(receipt.get("lines")).hasSize(3);
        assertThat(line(receipt, "Milk 1L").get("discrepancy").asText()).isEqualTo("OVER");
        assertThat(line(receipt, "Milk 1L").get("delta").asInt()).isEqualTo(12);
        assertThat(line(receipt, "Butter 500g").get("discrepancy").asText()).isEqualTo("UNIT_MISMATCH");
        assertThat(line(receipt, "Butter 500g").get("delta").isNull()).isTrue();
        assertThat(line(receipt, "Cheddar").get("discrepancy").asText()).isEqualTo("SHORT");
        assertThat(line(receipt, "Cheddar").get("delta").asInt()).isEqualTo(-2);
        assertThat(receipt.get("poConfidence").asDouble()).isEqualTo(1.0);
    }

    @Test
    void assigningAPoToGarbageAddsEveryLineAsNotDelivered() throws Exception {
        String id = id(captured("garbage"));
        String body = assign(id, "PO-4502").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode receipt = json.readTree(body);
        assertThat(receipt.get("supplier").asText()).isEqualTo("Blue Ocean Seafood Co.");
        assertThat(receipt.get("lines")).hasSize(2);
        receipt.get("lines").forEach(l -> assertThat(l.get("discrepancy").asText()).isEqualTo("NOT_DELIVERED"));
        confirm(id).andExpect(status().isOk());
    }

    @Test
    void assignIsSafeToRepeatButCannotMoveToAnotherPo() throws Exception {
        String id = id(captured("note-3"));
        assign(id, "PO-4503").andExpect(status().isOk());
        assign(id, "PO-4503").andExpect(status().isOk());
        assertThat(read(id).get("lines")).hasSize(3);
        assign(id, "PO-4501").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PO_ALREADY_RESOLVED"));
    }

    @Test
    void assigningAnUnknownPoIs404AndBlankIs400() throws Exception {
        String id = id(captured("note-3"));
        assign(id, "PO-0000").andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("PO_NOT_FOUND"));
        assign(id, " ").andExpect(status().isBadRequest());
    }

    @Test
    void concurrentConfirmAndDisputeHaveOneWinner() throws Exception {
        for (int round = 0; round < 5; round++) {
            String id = id(captured("note-1"));
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Callable<Integer>> calls = new ArrayList<>();
            calls.add(() -> confirm(id).andReturn().getResponse().getStatus());
            calls.add(() -> dispute(id, "{\"reason\":\"x\"}").andReturn().getResponse().getStatus());
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : pool.invokeAll(calls)) {
                statuses.add(result.get());
            }
            pool.shutdown();
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        }
    }
}
