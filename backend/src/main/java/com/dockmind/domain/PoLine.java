package com.dockmind.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "po_lines")
public class PoLine {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "po_id")
    private PurchaseOrder purchaseOrder;

    private String sku;

    @Column(nullable = false)
    private String description;

    @Column(name = "ordered_qty", nullable = false, precision = 19, scale = 3)
    private BigDecimal orderedQty;

    @Column(nullable = false)
    private String unit;

    protected PoLine() {
    }

    public String getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getOrderedQty() {
        return orderedQty;
    }

    public String getUnit() {
        return unit;
    }
}
