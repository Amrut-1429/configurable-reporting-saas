package com.reporting.repository;

import com.reporting.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM Transaction t WHERE t.file.id = :fileId")
    void deleteByFileId(@org.springframework.data.repository.query.Param("fileId") Long fileId);
    Page<Transaction> findByFileId(Long fileId, Pageable pageable);

    boolean existsByLocationAndProductAndTransactionDate(com.reporting.entity.Location location, com.reporting.entity.Product product, java.time.LocalDate transactionDate);
}
