package com.dockmind.api;

import com.dockmind.domain.PurchaseOrder;
import com.dockmind.domain.PurchaseOrderRepository;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PurchaseOrderController {
    private final PurchaseOrderRepository purchaseOrders;

    public PurchaseOrderController(PurchaseOrderRepository purchaseOrders) {
        this.purchaseOrders = purchaseOrders;
    }

    @GetMapping("/purchase-orders")
    @Transactional(readOnly = true)
    public List<PurchaseOrderView> list(@RequestParam(required = false) String status) {
        List<PurchaseOrder> found = StringUtils.hasText(status)
                ? purchaseOrders.findByStatusOrderById(status.trim())
                : purchaseOrders.findAllByOrderById();
        return found.stream().map(PurchaseOrderView::from).toList();
    }
}
