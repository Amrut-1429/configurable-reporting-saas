package com.reporting.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.math.BigDecimal;

@Data
@Builder
public class TransactionDTO {
    private Long id;
    
    private LocalDate transactionDate;
    private String invoiceNumber;
    
    private String locationName;
    private String productNumber;
    private String productName;
    
    private BigDecimal saleAmount;
    private BigDecimal purchaseAmount;
    private BigDecimal expenseAmount;
    private BigDecimal quantity;
    private BigDecimal taxAmount;
}
