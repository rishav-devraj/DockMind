package com.dockmind.domain;

public enum ReceiptStatus {
    CAPTURED,
    EXTRACTED,
    NEEDS_MANUAL,
    CONFIRMED,
    DISPUTED;

    public boolean isTerminal() {
        return this == CONFIRMED || this == DISPUTED;
    }
}
