package com.dockmind.domain;

import com.dockmind.matching.Discrepancy;
import com.dockmind.matching.Matcher;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "receipt_lines")
public class ReceiptLine implements Matcher.Line {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "receipt_line_seq")
    @SequenceGenerator(name = "receipt_line_seq", sequenceName = "receipt_line_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receipt_id")
    private Receipt receipt;

    private String poLineId;

    @Column(length = Receipt.TEXT_LIMIT)
    private String description;

    @Column(precision = 19, scale = 3)
    private BigDecimal orderedQty;

    @Column(precision = 19, scale = 3)
    private BigDecimal receivedQty;

    private String unit;
    private Double confidence;

    @Enumerated(EnumType.STRING)
    private Discrepancy discrepancy;

    @Column(precision = 19, scale = 3)
    private BigDecimal delta;

    @Column(precision = 19, scale = 3)
    private BigDecimal aiQty;

    private boolean correctedByClerk;
    private boolean needsReview;

    protected ReceiptLine() {
    }

    ReceiptLine(Receipt receipt) {
        this.receipt = receipt;
    }

    public Long getId() {
        return id;
    }

    public String getPoLineId() {
        return poLineId;
    }

    public void setPoLineId(String poLineId) {
        this.poLineId = poLineId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getOrderedQty() {
        return orderedQty;
    }

    public void setOrderedQty(BigDecimal orderedQty) {
        this.orderedQty = orderedQty;
    }

    public BigDecimal getReceivedQty() {
        return receivedQty;
    }

    public void setReceivedQty(BigDecimal receivedQty) {
        this.receivedQty = receivedQty;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Discrepancy getDiscrepancy() {
        return discrepancy;
    }

    public void setDiscrepancy(Discrepancy discrepancy) {
        this.discrepancy = discrepancy;
    }

    public BigDecimal getDelta() {
        return delta;
    }

    public void setDelta(BigDecimal delta) {
        this.delta = delta;
    }

    public BigDecimal getAiQty() {
        return aiQty;
    }

    public void setAiQty(BigDecimal aiQty) {
        this.aiQty = aiQty;
    }

    public boolean isCorrectedByClerk() {
        return correctedByClerk;
    }

    public void setCorrectedByClerk(boolean correctedByClerk) {
        this.correctedByClerk = correctedByClerk;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }

    public void setNeedsReview(boolean needsReview) {
        this.needsReview = needsReview;
    }
}
