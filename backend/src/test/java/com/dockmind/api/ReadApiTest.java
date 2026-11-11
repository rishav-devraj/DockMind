package com.dockmind.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ReadApiTest {
    @Autowired
    MockMvc mvc;

    @Test
    void listsOpenPurchaseOrdersWithLines() throws Exception {
        mvc.perform(get("/purchase-orders").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[1].id").value("PO-4502"))
                .andExpect(jsonPath("$[1].supplier").value("Blue Ocean Seafood Co."))
                .andExpect(jsonPath("$[1].lines", hasSize(2)))
                .andExpect(jsonPath("$[1].lines[0].id").value("POL-4502-1"))
                .andExpect(jsonPath("$[1].lines[0].sku").value("SKU-SAL-10"))
                .andExpect(jsonPath("$[1].lines[0].orderedQty").value(80))
                .andExpect(jsonPath("$[1].lines[0].unit").value("KG"));
    }

    @Test
    void unknownPurchaseOrderStatusIsAnEmptyList() throws Exception {
        mvc.perform(get("/purchase-orders").param("status", "CLOSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void unknownReceiptIs404InContractShape() throws Exception {
        mvc.perform(get("/receipts/RCP-999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RECEIPT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(get("/receipts/not-an-id")).andExpect(status().isNotFound());
    }

    @Test
    void invalidReceiptStatusFilterIs400() throws Exception {
        mvc.perform(get("/receipts").param("status", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS"));
    }

    @Test
    void blankReceiptStatusFilterListsAll() throws Exception {
        mvc.perform(get("/receipts").param("status", "")).andExpect(status().isOk());
    }
}
