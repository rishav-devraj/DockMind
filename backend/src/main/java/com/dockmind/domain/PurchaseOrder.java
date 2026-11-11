package com.dockmind.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {
    @Id
    private String id;

    @Column(name = "po_number", nullable = false, unique = true)
    private String poNumber;

    @Column(nullable = false)
    private String supplier;

    @Column(nullable = false)
    private String status;

    @OneToMany(mappedBy = "purchaseOrder")
    @OrderBy("id ASC")
    private List<PoLine> lines = new ArrayList<>();

    protected PurchaseOrder() {
    }

    public String getId() {
        return id;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public String getSupplier() {
        return supplier;
    }

    public String getStatus() {
        return status;
    }

    public List<PoLine> getLines() {
        return lines;
    }
}
