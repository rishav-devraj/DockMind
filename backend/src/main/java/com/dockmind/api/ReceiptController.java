package com.dockmind.api;

import com.dockmind.domain.ReceiptStatus;
import com.dockmind.service.CaptureService;
import com.dockmind.service.ReceiptService;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/receipts")
public class ReceiptController {
    private final ReceiptService receipts;
    private final CaptureService capture;

    public ReceiptController(ReceiptService receipts, CaptureService capture) {
        this.receipts = receipts;
        this.capture = capture;
    }

    @GetMapping
    public List<ReceiptView> list(@RequestParam(required = false) String status) {
        return receipts.list(parseStatus(status));
    }

    @GetMapping("/{id}")
    public ReceiptView get(@PathVariable String id) {
        return receipts.get(id);
    }

    @PostMapping
    public ResponseEntity<ReceiptView> capture(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Fixture", required = false) String fixture,
            @RequestParam("image") MultipartFile image) throws IOException {
        CaptureService.Captured result =
                capture.capture(idempotencyKey, image.getBytes(), fixture, image.getOriginalFilename());
        return ResponseEntity.status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(result.receipt());
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable String id) {
        ReceiptService.StoredImage image = receipts.image(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.mimeType()))
                .header("X-Content-Type-Options", "nosniff")
                .body(image.data());
    }

    public record CorrectLineRequest(BigDecimal receivedQty, String unit) {
    }

    public record DisputeRequest(String reason) {
    }

    public record AssignPoRequest(String poNumber) {
    }

    @PatchMapping("/{id}/lines/{lineId}")
    public ReceiptView correctLine(@PathVariable String id, @PathVariable String lineId,
            @RequestBody CorrectLineRequest body) {
        return receipts.correctLine(id, lineId, body.receivedQty(), body.unit());
    }

    @PostMapping("/{id}/confirm")
    public ReceiptView confirm(@PathVariable String id) {
        return receipts.confirm(id);
    }

    @PostMapping("/{id}/dispute")
    public ReceiptView dispute(@PathVariable String id, @RequestBody DisputeRequest body) {
        return receipts.dispute(id, body.reason());
    }

    @PatchMapping("/{id}")
    public ReceiptView assignPo(@PathVariable String id, @RequestBody AssignPoRequest body) {
        return receipts.assignPo(id, body.poNumber());
    }

    private static ReceiptStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return ReceiptStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_STATUS", "That receipt status is not recognised.");
        }
    }
}
