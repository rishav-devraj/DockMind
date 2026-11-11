package com.dockmind.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "receipts", uniqueConstraints =
        @UniqueConstraint(name = "uk_receipts_idempotency_key", columnNames = "idempotency_key"))
public class Receipt {
    public static final int TEXT_LIMIT = 2000;
    public static final int NAME_LIMIT = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "receipt_seq")
    @SequenceGenerator(name = "receipt_seq", sequenceName = "receipt_seq", initialValue = 1001, allocationSize = 1)
    private Long id;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReceiptStatus status = ReceiptStatus.CAPTURED;

    private String poNumber;
    private String supplier;
    private Double overallConfidence;

    @Column(length = TEXT_LIMIT)
    private String aiNote;

    private String deliveryNoteNo;
    private Double poConfidence;

    @Column(length = TEXT_LIMIT)
    private String freeText;

    private boolean needsReview = true;

    @Column(length = TEXT_LIMIT)
    private String disputeReason;

    @Column(nullable = false)
    private String clerk;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Kept so a receipt recovered after a restart is read the same way as on first capture. */
    private String fixtureHeader;
    private String fileName;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ReceiptLine> lines = new ArrayList<>();

    protected Receipt() {
    }

    public Receipt(String idempotencyKey, String clerk, String fixtureHeader, String fileName) {
        this.idempotencyKey = idempotencyKey;
        this.clerk = clerk;
        this.fixtureHeader = fixtureHeader;
        this.fileName = fileName;
        this.createdAt = now();
        this.updatedAt = this.createdAt;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    public void touch() {
        updatedAt = now();
    }

    public ReceiptLine newLine() {
        ReceiptLine line = new ReceiptLine(this);
        lines.add(line);
        return line;
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public ReceiptStatus getStatus() {
        return status;
    }

    public void setStatus(ReceiptStatus status) {
        this.status = status;
    }

    public String getPoNumber() {
        return poNumber;
    }

    public void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
    }

    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }

    public Double getOverallConfidence() {
        return overallConfidence;
    }

    public void setOverallConfidence(Double overallConfidence) {
        this.overallConfidence = overallConfidence;
    }

    public String getAiNote() {
        return aiNote;
    }

    public void setAiNote(String aiNote) {
        this.aiNote = aiNote;
    }

    public String getDeliveryNoteNo() {
        return deliveryNoteNo;
    }

    public void setDeliveryNoteNo(String deliveryNoteNo) {
        this.deliveryNoteNo = deliveryNoteNo;
    }

    public Double getPoConfidence() {
        return poConfidence;
    }

    public void setPoConfidence(Double poConfidence) {
        this.poConfidence = poConfidence;
    }

    public String getFreeText() {
        return freeText;
    }

    public void setFreeText(String freeText) {
        this.freeText = freeText;
    }

    public boolean isNeedsReview() {
        return needsReview;
    }

    public void setNeedsReview(boolean needsReview) {
        this.needsReview = needsReview;
    }

    public String getDisputeReason() {
        return disputeReason;
    }

    public void setDisputeReason(String disputeReason) {
        this.disputeReason = disputeReason;
    }

    public String getClerk() {
        return clerk;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getFixtureHeader() {
        return fixtureHeader;
    }

    public String getFileName() {
        return fileName;
    }

    public List<ReceiptLine> getLines() {
        return lines;
    }
}
