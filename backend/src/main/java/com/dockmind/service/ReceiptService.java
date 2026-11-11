package com.dockmind.service;

import com.dockmind.ai.Extractor.ExtractionRequest;
import com.dockmind.ai.Extractor.OpenPo;
import com.dockmind.ai.Extractor.OpenPoLine;
import com.dockmind.ai.ValidatedExtraction;
import com.dockmind.api.ApiException;
import com.dockmind.api.PurchaseOrderView;
import com.dockmind.api.ReceiptView;
import com.dockmind.domain.PoLine;
import com.dockmind.domain.PoNumbers;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceiptService {
    static final String CLERK = "clerk-01";
    private static final String OPEN = "OPEN";
    private static final BigDecimal MAX_QTY = new BigDecimal("1000000000");

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

    public record PageOf<T>(List<T> items, long total) {
    }

    /** A2, newest first, one page at a time. */
    @Transactional(readOnly = true)
    public PageOf<ReceiptView> list(ReceiptStatus status, Pageable page) {
        Page<Receipt> found = status == null
                ? receipts.findAllByOrderByCreatedAtDescIdDesc(page)
                : receipts.findByStatusOrderByCreatedAtDescIdDesc(status, page);
        return new PageOf<>(found.map(ReceiptView::from).getContent(), found.getTotalElements());
    }

    /** A1. {@code query} matches part of a PO number or supplier, ignoring case. */
    @Transactional(readOnly = true)
    public PageOf<PurchaseOrderView> purchaseOrders(String status, String query, Pageable page) {
        String pattern = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        Page<PurchaseOrder> found = purchaseOrders.search(status, pattern, page);
        return new PageOf<>(found.map(PurchaseOrderView::from).getContent(), found.getTotalElements());
    }

    /** One indexed lookup per read, so the cost does not grow with the number of POs. */
    @Transactional(readOnly = true)
    public Optional<OpenPo> findOpenPo(String writtenPoNumber) {
        List<String> keys = PoNumbers.lookupKeys(writtenPoNumber);
        if (keys.isEmpty()) {
            return Optional.empty();
        }
        return purchaseOrders.findByPoNumberInAndStatus(keys, OPEN).stream()
                .findFirst()
                .map(po -> new OpenPo(po.getPoNumber(), po.getSupplier(), po.getLines().stream()
                        .map(line -> new OpenPoLine(line.getId(), line.getDescription(), line.getOrderedQty(), line.getUnit()))
                        .toList()));
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
        return receipts.findByStatus(ReceiptStatus.CAPTURED).stream().map(Receipt::getId).toList();
    }

    /** What the reader needs, or empty if this receipt no longer waits for extraction. */
    @Transactional(readOnly = true)
    public Optional<ExtractionRequest> extractionRequest(Long id) {
        return receipts.findById(id)
                .filter(receipt -> receipt.getStatus() == ReceiptStatus.CAPTURED)
                .flatMap(receipt -> images.findById(id).map(image -> new ExtractionRequest(image.getData(),
                        image.getMimeType(), receipt.getFileName(), receipt.getFixtureHeader())));
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
            line.setReceivedQty(extracted.qty() == null ? BigDecimal.ZERO : extracted.qty());
            line.setAiQty(extracted.qty());
            line.setUnit(extracted.unit());
            line.setConfidence(extracted.confidence());
        }
        if (!result.manual()) {
            // The reader never saw the PO, so its lines are tied to the PO's lines here, in code.
            matcher.linkByDescription(receipt.getLines(), orderLines(receipt.getPoNumber()));
        }
        recompute(receipt);
        receipt.setStatus(result.manual() ? ReceiptStatus.NEEDS_MANUAL : ReceiptStatus.EXTRACTED);
        receipt.touch();
    }

    /** The clerk's number always wins over the reader's. */
    @Transactional
    public ReceiptView correctLine(String id, String lineId, BigDecimal receivedQty, String unit) {
        if (receivedQty == null || receivedQty.signum() < 0 || receivedQty.compareTo(MAX_QTY) > 0) {
            throw ApiException.badRequest("INVALID_QUANTITY", "receivedQty must be a number from 0 to 1000000000.");
        }
        String normalizedUnit = Matcher.normalizeUnit(unit);
        if (unit != null && normalizedUnit == null) {
            throw ApiException.badRequest("INVALID_UNIT", "unit must not be blank.");
        }
        Receipt receipt = lockReceipt(id);
        Long parsedLineId = ReceiptView.parseLineId(lineId);
        ReceiptLine line = receipt.getLines().stream()
                .filter(candidate -> candidate.getId().equals(parsedLineId))
                .findFirst()
                .orElseThrow(ApiException::lineNotFound);
        if (receipt.getStatus().isTerminal()) {
            throw ApiException.conflict("RECEIPT_CLOSED", "This receipt is already " + receipt.getStatus() + " and cannot be edited.");
        }
        line.setReceivedQty(receivedQty.setScale(3, RoundingMode.HALF_UP));
        if (normalizedUnit != null) {
            line.setUnit(normalizedUnit);
        }
        line.setCorrectedByClerk(true);
        recompute(receipt);
        receipt.touch();
        return ReceiptView.from(receipt);
    }

    @Transactional
    public ReceiptView confirm(String id) {
        Receipt receipt = lockReceipt(id);
        switch (receipt.getStatus()) {
            case CONFIRMED -> {
                return ReceiptView.from(receipt);
            }
            case DISPUTED -> throw ApiException.conflict("ALREADY_DISPUTED", "This receipt was disputed and cannot be confirmed.");
            case CAPTURED -> throw notReady();
            default -> {
            }
        }
        if (receipt.getPoNumber() == null) {
            throw ApiException.conflict("PO_UNRESOLVED", "Choose the purchase order before confirming.");
        }
        receipt.setStatus(ReceiptStatus.CONFIRMED);
        receipt.touch();
        return ReceiptView.from(receipt);
    }

    @Transactional
    public ReceiptView dispute(String id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("REASON_REQUIRED", "A reason is required to dispute a receipt.");
        }
        if (reason.length() > Receipt.TEXT_LIMIT) {
            throw ApiException.badRequest("REASON_TOO_LONG", "The reason must be 2000 characters or fewer.");
        }
        Receipt receipt = lockReceipt(id);
        switch (receipt.getStatus()) {
            case DISPUTED -> {
                return ReceiptView.from(receipt);
            }
            case CONFIRMED -> throw ApiException.conflict("ALREADY_CONFIRMED", "This receipt was confirmed and cannot be disputed.");
            case CAPTURED -> throw notReady();
            default -> {
            }
        }
        receipt.setDisputeReason(reason.trim());
        receipt.setStatus(ReceiptStatus.DISPUTED);
        receipt.touch();
        return ReceiptView.from(receipt);
    }

    /** The clerk has identified the PO, so it carries full confidence and no longer flags the receipt. */
    @Transactional
    public ReceiptView assignPo(String id, String poNumber) {
        if (poNumber == null || poNumber.isBlank()) {
            throw ApiException.badRequest("PO_REQUIRED", "poNumber is required.");
        }
        Receipt receipt = lockReceipt(id);
        PurchaseOrder po = purchaseOrders.findByPoNumber(poNumber.trim()).orElseThrow(ApiException::poNotFound);
        switch (receipt.getStatus()) {
            case CAPTURED -> throw notReady();
            case CONFIRMED, DISPUTED ->
                throw ApiException.conflict("RECEIPT_CLOSED", "This receipt is already " + receipt.getStatus() + " and cannot be edited.");
            case EXTRACTED -> {
                if (po.getPoNumber().equals(receipt.getPoNumber())) {
                    return ReceiptView.from(receipt);
                }
                throw ApiException.conflict("PO_ALREADY_RESOLVED", "This receipt already has a purchase order.");
            }
            default -> {
            }
        }
        receipt.setPoNumber(po.getPoNumber());
        receipt.setSupplier(po.getSupplier());
        receipt.setPoConfidence(1.0);
        matcher.relinkByDescription(receipt.getLines(), orderLines(po));
        recompute(receipt);
        receipt.setStatus(ReceiptStatus.EXTRACTED);
        receipt.touch();
        return ReceiptView.from(receipt);
    }

    private Receipt lockReceipt(String id) {
        return receipts.findForUpdate(ReceiptView.parseReceiptId(id)).orElseThrow(ApiException::receiptNotFound);
    }

    private static ApiException notReady() {
        return ApiException.conflict("NOT_READY", "This note is still being read. Try again in a moment.");
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
