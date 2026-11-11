package com.dockmind.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

/** The expected outcome of each extra note in the kit, through the whole pipeline. */
@SpringBootTest
@AutoConfigureMockMvc
class KitNotesApiTest extends ApiTestSupport {

    private static String result(JsonNode receipt, String description) {
        return line(receipt, description).get("discrepancy").asText();
    }

    private static void assertManualWithNoPo(JsonNode receipt) {
        assertThat(receipt.get("status").asText()).isEqualTo("NEEDS_MANUAL");
        assertThat(receipt.get("poNumber").isNull()).isTrue();
        receipt.get("lines").forEach(l -> assertThat(l.get("poLineId").isNull()).isTrue());
    }

    @Test
    void overDeliveryWithUnitAliases() throws Exception {
        JsonNode receipt = captured("note-4");
        assertThat(result(receipt, "Gala Apples")).isEqualTo("OVER");
        assertThat(line(receipt, "Gala Apples").get("delta").asInt()).isEqualTo(20);
        assertThat(result(receipt, "Oranges, Navel")).isEqualTo("MATCH");
        assertThat(result(receipt, "Lettuce, Romaine")).isEqualTo("MATCH");
    }

    @Test
    void siblingPoNumberResolvesWithItsOwnSupplier() throws Exception {
        JsonNode receipt = captured("note-5");
        assertThat(receipt.get("poNumber").asText()).isEqualTo("PO-4513");
        assertThat(result(receipt, "Eggs, large, tray")).isEqualTo("MATCH");
    }

    @Test
    void legiblePoFromAnotherSupplierEndsManual() throws Exception {
        JsonNode receipt = captured("note-6");
        assertManualWithNoPo(receipt);
        assertThat(receipt.get("supplier").asText()).isEqualTo("Green Valley Eggs");
    }

    @Test
    void noteWithNoPoEndsManual() throws Exception {
        assertManualWithNoPo(captured("note-7"));
    }

    @Test
    void crossedOutQuantityUsesTheCorrection() throws Exception {
        JsonNode receipt = captured("note-8");
        assertThat(result(receipt, "Salmon fillet")).isEqualTo("SHORT");
        assertThat(line(receipt, "Salmon fillet").get("delta").asInt()).isEqualTo(-2);
        assertThat(line(receipt, "Salmon fillet").get("needsReview").asBoolean()).isTrue();
        assertThat(result(receipt, "Tiger Prawns")).isEqualTo("MATCH");
    }

    @Test
    void somethingThatIsNotADeliveryNoteEndsManual() throws Exception {
        assertManualWithNoPo(captured("note-9"));
    }
}
