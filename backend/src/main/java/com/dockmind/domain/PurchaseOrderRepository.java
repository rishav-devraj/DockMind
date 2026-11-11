package com.dockmind.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, String> {
    List<PurchaseOrder> findByStatusOrderById(String status);

    List<PurchaseOrder> findAllByOrderById();

    Optional<PurchaseOrder> findByPoNumber(String poNumber);
}
