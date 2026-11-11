package com.dockmind.api;

import com.dockmind.service.ReceiptService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PurchaseOrderController {
    private final ReceiptService service;

    public PurchaseOrderController(ReceiptService service) {
        this.service = service;
    }

    /** A1. Pages of 50 by default. {@code q} searches PO number and supplier. */
    @GetMapping("/purchase-orders")
    public ResponseEntity<List<PurchaseOrderView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        String wanted = StringUtils.hasText(status) ? status.trim() : null;
        return Paging.respond(service.purchaseOrders(wanted, q, Paging.of(page, size)));
    }
}
