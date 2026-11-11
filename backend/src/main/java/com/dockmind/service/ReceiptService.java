package com.dockmind.service;

import com.dockmind.ai.Extractor.ExtractionRequest;
import com.dockmind.ai.Extractor.OpenPo;
import com.dockmind.ai.Extractor.OpenPoLine;
import com.dockmind.ai.ValidatedExtraction;
import com.dockmind.api.ApiException;
import com.dockmind.api.ReceiptView;
import com.dockmind.domain.PoLine;
import com.dockmind.domain.PurchaseOrder;
import com.dockmind.domain.PurchaseOrderRepository;
import com.dockmind.domain.Receipt;
import com.dockmind.domain.ReceiptImage;
import com.dockmind.domain.ReceiptImageRepository;
import com.dockmind.domain.ReceiptLine;
import com.dockmind.domain.ReceiptRepository;
import com.dockmind.domain.ReceiptStatus;
import com.dockmind.matching.Matcher;
import com.dockmind.matching.Matcher.OrderLine;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceiptService {
    static final String CLERK = "clerk-01";
    private static final String OPEN = "OPEN";

    private final ReceiptRepository receipts;
    private final ReceiptImageRepository images;
    private final PurchaseOrderRepository purchaseOrders;
    private final Matcher matcher;

    public ReceiptService(ReceiptRepository receipts, ReceiptImageRepository images,
            PurchaseOrderRepository purchaseOrders, Matcher matcher) {
        this.receipts = receipts;
        this.images = images;
        this.purchaseOrders = purchaseOrders;
        this.matcher = matcher;
    }

    public record StoredImage(byte[] data, String mimeType) {
    }

    @Transactional(readOnly = true)
    public List<ReceiptView> list(ReceiptStatus status) {
        List<Receipt> found = status == null
                ? receipts.findAllByOrderByCreatedAtDescIdDesc()
                : receipts.findByStatusOrderByCreatedAtDescIdDesc(status);
        return found.stream().map(ReceiptView::from).toList();
    }

    @Transactional(readOnly = true)
    public ReceiptView get(String id) {
        return ReceiptView.from(receipts.findById(ReceiptView.parseReceiptId(id)).orElseThrow(ApiException::receiptNotFound));
    }

    @Transactional(readOnly = true)
    public StoredImage image(String id) {
        ReceiptImage image = images.findById(ReceiptView.parseReceiptId(id)).orElseThrow(ApiException::receiptNotFound);
        return new StoredImage(image.getData(), image.getMimeType());
    }

    @Transactional(readOnly = true)
    public Optional<ReceiptView> findByKey(String idempotencyKey) {
        return receipts.findByIdempotencyKey(idempotencyKey).map(ReceiptView::from);
    }

    /** Receipt and photo commit together. Flushing here surfaces a duplicate key to the caller. */
    @Transactional
    public Created create(String idempotencyKey, byte[] image, String mimeType, String fixtureHeader, String fileName) {
        Receipt receipt = receipts.saveAndFlush(new Receipt(idempotencyKey, CLERK, fixtureHeader, fileName));
        images.save(new ReceiptImage(receipt.getId(), image, mimeType));
        return new Created(receipt.getId(), ReceiptView.from(receipt));
    }

    public record Created(Long id, ReceiptView view) {
    }

    @Transactional(readOnly = true)
    public List<Long> stuckInCaptured() {
        return receipts.findByStatusOrderByCreatedAtDescIdDesc(ReceiptStatus.CAPTURED).stream().map(Receipt::getId).toList();
    }

    /** What the reader needs, or empty if this receipt no longer waits for extraction. */
    @Transactional(readOnly = true)
    public Optional<ExtractionRequest> extractionRequest(Long id) {
        return receipts.findById(id)
                .filter(receipt -> receipt.getStatus() == ReceiptStatus.CAPTURED)
                .flatMap(receipt -> images.findById(id).map(image -> new ExtractionRequest(image.getData(),
                        image.getMimeType(), receipt.getFileName(), receipt.getFixtureHeader(), openPos())));
    }

    private List<OpenPo> openPos() {
        return purchaseOrders.findByStatusOrderById(OPEN).stream()
                .map(po -> new OpenPo(po.getPoNumber(), po.getSupplier(), po.getLines().stream()
                        .map(line -> new OpenPoLine(line.getId(), line.getDescription(), line.getOrderedQty(), line.getUnit()))
                        .toList()))
                .toList();
    }

    /** Saves a validated result. Applies only while CAPTURED, so a re-run cannot overwrite later work. */
    @Transactional
    public void applyExtraction(Long id, ValidatedExtraction result) {
        Receipt receipt = receipts.findForUpdate(id).orElse(null);
        if (receipt == null || receipt.getStatus() != ReceiptStatus.CAPTURED) {
            return;
        }
        receipt.setPoNumber(result.poNumber());
        receipt.setSupplier(result.supplier());
        receipt.setDeliveryNoteNo(result.deliveryNoteNo());
        receipt.setPoConfidence(result.poConfidence());
        receipt.setFreeText(result.freeText());
        receipt.setOverallConfidence(result.overallConfidence());
        receipt.setAiNote(result.aiNote());
        for (ValidatedExtraction.Line extracted : result.lines()) {
            ReceiptLine line = receipt.newLine();
            line.setDescription(extracted.description());
            line.setPoLineId(extracted.poLineId());
            line.setReceivedQty(extracted.qty() == null ? java.math.BigDecimal.ZERO : extracted.qty());
            line.setAiQty(extracted.qty());
            line.setUnit(extracted.unit());
            line.setConfidence(extracted.confidence());
        }
        recompute(receipt);
        receipt.setStatus(result.manual() ? ReceiptStatus.NEEDS_MANUAL : ReceiptStatus.EXTRACTED);
        receipt.touch();
    }

    private void recompute(Receipt receipt) {
        matcher.reconcile(receipt.getLines(), orderLines(receipt.getPoNumber()), receipt::newLine);
        receipt.setNeedsReview(matcher.receiptNeedsReview(receipt.getLines(), receipt.getPoNumber(), receipt.getPoConfidence()));
    }

    private List<OrderLine> orderLines(String poNumber) {
        if (poNumber == null) {
            return List.of();
        }
        return purchaseOrders.findByPoNumber(poNumber).map(ReceiptService::orderLines).orElse(List.of());
    }

    private static List<OrderLine> orderLines(PurchaseOrder po) {
        return po.getLines().stream().map(ReceiptService::orderLine).toList();
    }

    private static OrderLine orderLine(PoLine line) {
        return new OrderLine(line.getId(), line.getDescription(), line.getOrderedQty(), line.getUnit());
    }
}
