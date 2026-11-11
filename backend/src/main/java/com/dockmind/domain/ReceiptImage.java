package com.dockmind.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/** The proof of delivery. Its own table, written in the receipt's transaction, so lists never load the bytes. */
@Entity
@Table(name = "receipt_images")
public class ReceiptImage {
    @Id
    private Long receiptId;

    @Lob
    @Column(nullable = false)
    private byte[] data;

    @Column(nullable = false)
    private String mimeType;

    protected ReceiptImage() {
    }

    public ReceiptImage(Long receiptId, byte[] data, String mimeType) {
        this.receiptId = receiptId;
        this.data = data;
        this.mimeType = mimeType;
    }

    public byte[] getData() {
        return data;
    }

    public String getMimeType() {
        return mimeType;
    }
}
