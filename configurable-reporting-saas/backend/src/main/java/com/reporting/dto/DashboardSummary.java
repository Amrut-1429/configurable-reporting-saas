package com.reporting.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DashboardSummary {
    private BigDecimal totalAmount;
    private Long totalQuantity;
    private Long totalRecords;
    private String topLocation;
    private String topProduct;
    
    private List<Map<String, Object>> locationWise;
    private List<Map<String, Object>> dateTrend;
    private List<Map<String, Object>> productWise;
}
