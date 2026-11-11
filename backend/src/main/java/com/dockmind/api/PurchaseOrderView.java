package com.dockmind.api;

import com.dockmind.domain.PoLine;
import com.dockmind.domain.PurchaseOrder;
import java.math.BigDecimal;
import java.util.List;

public record PurchaseOrderView(String id, String poNumber, String supplier, String status, List<Line> lines) {
    public record Line(String id, String sku, String description, BigDecimal orderedQty, String unit) {
        static Line from(PoLine line) {
            return new Line(line.getId(), line.getSku(), line.getDescription(),
                    ReceiptView.plain(line.getOrderedQty()), line.getUnit());
        }
    }

    public static PurchaseOrderView from(PurchaseOrder po) {
        return new PurchaseOrderView(po.getId(), po.getPoNumber(), po.getSupplier(), po.getStatus(),
                po.getLines().stream().map(Line::from).toList());
    }
}
