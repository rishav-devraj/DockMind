package com.dockmind.service;

import com.dockmind.api.ApiException;
import com.dockmind.api.ReceiptView;
import com.dockmind.domain.Receipt;
import com.dockmind.domain.ReceiptRepository;
import com.dockmind.domain.ReceiptStatus;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceiptService {
    private final ReceiptRepository receipts;

    public ReceiptService(ReceiptRepository receipts) {
        this.receipts = receipts;
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
}
