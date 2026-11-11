package com.dockmind.api;

import com.dockmind.domain.ReceiptStatus;
import com.dockmind.service.ReceiptService;
import java.util.List;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/receipts")
public class ReceiptController {
    private final ReceiptService receipts;

    public ReceiptController(ReceiptService receipts) {
        this.receipts = receipts;
    }

    @GetMapping
    public List<ReceiptView> list(@RequestParam(required = false) String status) {
        return receipts.list(parseStatus(status));
    }

    @GetMapping("/{id}")
    public ReceiptView get(@PathVariable String id) {
        return receipts.get(id);
    }

    private static ReceiptStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return ReceiptStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_STATUS", "That receipt status is not recognised.");
        }
    }
}
