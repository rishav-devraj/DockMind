package com.dockmind.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, String> {
    Optional<PurchaseOrder> findByPoNumber(String poNumber);

    /** Uses the unique index on po_number: one lookup per read, however many POs exist. */
    List<PurchaseOrder> findByPoNumberInAndStatus(Collection<String> poNumbers, String status);

    /** A1. {@code query} is a lower-case LIKE pattern on PO number or supplier, or null for all. */
    @Query("""
            select p from PurchaseOrder p
            where (:status is null or p.status = :status)
              and (:query is null or lower(p.poNumber) like :query or lower(p.supplier) like :query)
            order by p.id""")
    Page<PurchaseOrder> search(@Param("status") String status, @Param("query") String query, Pageable page);
}
