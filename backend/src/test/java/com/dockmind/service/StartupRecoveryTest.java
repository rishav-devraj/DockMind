package com.dockmind.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dockmind.api.ApiException;
import com.dockmind.api.ReceiptView;
import com.dockmind.domain.ReceiptStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StartupRecoveryTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @Autowired
    ReceiptService receipts;

    @Autowired
    ExtractionRunner runner;

    /** A receipt saved but never submitted is what a crash between capture and extraction leaves behind. */
    @Test
    void receiptStuckInCapturedIsReadAgainWithItsOriginalFixture() throws Exception {
        ReceiptService.Created stuck = receipts.create(UUID.randomUUID().toString(), PNG, "image/png", "note-2", "photo.png");
        assertThat(stuck.view().status()).isEqualTo(ReceiptStatus.CAPTURED);

        runner.recoverStuckReceipts();

        ReceiptView recovered = awaitExtraction(stuck.view().id());
        assertThat(recovered.status()).isEqualTo(ReceiptStatus.EXTRACTED);
        assertThat(recovered.poNumber()).isEqualTo("PO-4502");
    }

    @Test
    void reRunningAFinishedReceiptChangesNothing() throws ApiException, InterruptedException {
        ReceiptService.Created created = receipts.create(UUID.randomUUID().toString(), PNG, "image/png", "note-1", "photo.png");
        runner.run(created.id());
        ReceiptView first = receipts.get(created.view().id());
        runner.run(created.id());
        ReceiptView second = receipts.get(created.view().id());
        assertThat(second.lines()).hasSameSizeAs(first.lines()).hasSize(3);
    }

    private ReceiptView awaitExtraction(String id) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            ReceiptView view = receipts.get(id);
            if (view.status() != ReceiptStatus.CAPTURED) {
                return view;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Receipt " + id + " stayed in CAPTURED");
    }
}
