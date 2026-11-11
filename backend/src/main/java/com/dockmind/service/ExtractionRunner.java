package com.dockmind.service;

import com.dockmind.ai.Extractor;
import com.dockmind.ai.Extractor.ExtractionRequest;
import com.dockmind.ai.ExtractionValidator;
import com.dockmind.ai.ValidatedExtraction;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Reads notes off the request thread: a vision call can outlast a dock connection. */
@Component
public class ExtractionRunner {
    private static final Logger log = LoggerFactory.getLogger(ExtractionRunner.class);

    private final Extractor extractor;
    private final ExtractionValidator validator;
    private final ReceiptService receipts;
    private final ExecutorService executor;

    public ExtractionRunner(Extractor extractor, ExtractionValidator validator, ReceiptService receipts,
            ExecutorService extractionExecutor) {
        this.extractor = extractor;
        this.validator = validator;
        this.receipts = receipts;
        this.executor = extractionExecutor;
    }

    public void submit(Long receiptId) {
        executor.execute(() -> run(receiptId));
    }

    /** A crash between capture and extraction leaves a receipt in CAPTURED. Read those again at startup. */
    @EventListener(ApplicationReadyEvent.class)
    void recoverStuckReceipts() {
        receipts.stuckInCaptured().forEach(this::submit);
    }

    void run(Long receiptId) {
        try {
            Optional<ExtractionRequest> request = receipts.extractionRequest(receiptId);
            if (request.isPresent()) {
                String raw = extractor.extract(request.get());
                receipts.applyExtraction(receiptId, validator.validate(raw, request.get().openPos()));
            }
        } catch (Exception e) {
            log.warn("Extraction failed for receipt {}", receiptId, e);
            fail(receiptId);
        }
    }

    private void fail(Long receiptId) {
        try {
            receipts.applyExtraction(receiptId, ValidatedExtraction.failed(ExtractionValidator.UNREADABLE_NOTE));
        } catch (Exception e) {
            log.error("Could not mark receipt {} for manual entry; it will be retried at startup", receiptId, e);
        }
    }
}
