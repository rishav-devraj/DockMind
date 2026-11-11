package com.dockmind.api;

import org.springframework.http.HttpStatus;

/** A failure the client is meant to see, rendered as { "error": CODE, "message": sentence }. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public static ApiException receiptNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RECEIPT_NOT_FOUND", "That receipt does not exist.");
    }

    public static ApiException lineNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "LINE_NOT_FOUND", "That line is not on this receipt.");
    }

    public static ApiException poNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PO_NOT_FOUND", "That purchase order does not exist.");
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
