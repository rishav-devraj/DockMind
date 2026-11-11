package com.dockmind.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {
    Page<Receipt> findAllByOrderByCreatedAtDescIdDesc(Pageable page);

    Page<Receipt> findByStatusOrderByCreatedAtDescIdDesc(ReceiptStatus status, Pageable page);

    List<Receipt> findByStatus(ReceiptStatus status);

    Optional<Receipt> findByIdempotencyKey(String idempotencyKey);

    /** Every state change goes through this lock, so a confirm and a dispute cannot both win. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Receipt r where r.id = :id")
    Optional<Receipt> findForUpdate(@Param("id") Long id);
}
