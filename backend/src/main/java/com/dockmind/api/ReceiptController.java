package com.dockmind.api;

import com.dockmind.domain.ReceiptStatus;
import com.dockmind.service.CaptureService;
import com.dockmind.service.ReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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

    /** A2. Newest first, pages of 50 by default. */
    @GetMapping
    public ResponseEntity<List<ReceiptView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Paging.respond(receipts.list(parseStatus(status), Paging.of(page, size)));
    }

    @GetMapping("/{id}")
    public ReceiptView get(@PathVariable String id) {
        return receipts.get(id);
    }

    /** Only describes the upload for Swagger. */
    public record CaptureForm(@Schema(type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED) String image) {
    }

    // Documented here rather than with consumes=, which would turn a non-multipart request's 400 into a 415.
    @Operation(requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content =
            @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = CaptureForm.class))))
    @PostMapping
    public ResponseEntity<ReceiptView> capture(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Fixture", required = false) String fixture,
            @Parameter(hidden = true) @RequestParam("image") MultipartFile image) throws IOException {
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
