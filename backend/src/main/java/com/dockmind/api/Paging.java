package com.dockmind.api;

import com.dockmind.service.ReceiptService.PageOf;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

/** Lists stay plain JSON arrays, as the contract has them. The total travels in a header. */
final class Paging {
    static final int DEFAULT_SIZE = 50;
    static final int MAX_SIZE = 200;
    static final String TOTAL_HEADER = "X-Total-Count";

    private Paging() {
    }

    static Pageable of(Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_SIZE : size;
        if (p < 0 || s < 1 || s > MAX_SIZE) {
            throw ApiException.badRequest("INVALID_PAGE", "page must be 0 or more, and size from 1 to " + MAX_SIZE + ".");
        }
        return PageRequest.of(p, s);
    }

    static <T> ResponseEntity<List<T>> respond(PageOf<T> page) {
        return ResponseEntity.ok().header(TOTAL_HEADER, String.valueOf(page.total())).body(page.items());
    }
}
