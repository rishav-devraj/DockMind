package com.dockmind.service;

import com.dockmind.api.ApiException;
import com.dockmind.api.ReceiptView;
import com.dockmind.domain.Receipt;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** Not transactional on purpose: the insert must commit or fail before duplicates are handled. */
@Service
public class CaptureService {
    private final ReceiptService receipts;
    private final ExtractionRunner runner;

    public CaptureService(ReceiptService receipts, ExtractionRunner runner) {
        this.receipts = receipts;
        this.runner = runner;
    }

    public record Captured(ReceiptView receipt, boolean created) {
    }

    public Captured capture(String idempotencyKey, byte[] image, String fixtureHeader, String fileName) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty() || key.length() > Receipt.NAME_LIMIT) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "The Idempotency-Key header must be 1 to 255 characters.");
        }
        if (image == null || image.length == 0) {
            throw ApiException.badRequest("MISSING_IMAGE", "A photo is required in the file field named image.");
        }
        String mimeType = sniffMimeType(image);
        if (mimeType == null) {
            throw ApiException.badRequest("INVALID_IMAGE", "The photo must be a JPEG or PNG.");
        }

        Optional<ReceiptView> existing = receipts.findByKey(key);
        if (existing.isPresent()) {
            return new Captured(existing.get(), false);
        }
        try {
            ReceiptService.Created created = receipts.create(key, image, mimeType, fixtureHeader, trim(fileName));
            runner.submit(created.id());
            return new Captured(created.view(), true);
        } catch (DataAccessException e) {
            // Lost a race on the unique key: the other request's receipt is the answer.
            return receipts.findByKey(key).map(view -> new Captured(view, false)).orElseThrow(() -> e);
        }
    }

    private static String trim(String fileName) {
        return fileName != null && fileName.length() > Receipt.NAME_LIMIT ? fileName.substring(0, Receipt.NAME_LIMIT) : fileName;
    }

    /** Judged from the bytes, since the client's declared type proves nothing. */
    private static String sniffMimeType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        return null;
    }
}
