package com.reporting.service;

import com.reporting.dto.DashboardSummary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final AggregationService aggregationService;

    public DashboardService(AggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    public DashboardSummary getSummary() {
        Map<String, Object> totals = aggregationService.getTotals();
        
        List<String> metrics = List.of("SUM_AMOUNT");

        BigDecimal amount = BigDecimal.ZERO;
        Object amountObj = totals.get("totalAmount");
        if (amountObj instanceof BigDecimal) {
            amount = (BigDecimal) amountObj;
        } else if (amountObj instanceof Number) {
            amount = BigDecimal.valueOf(((Number) amountObj).doubleValue());
        }

        Long qty = 0L;
        Object qtyObj = totals.get("totalQuantity");
        if (qtyObj instanceof Number) {
            qty = ((Number) qtyObj).longValue();
        }

        Long records = 0L;
        Object recordsObj = totals.get("totalRecords");
        if (recordsObj instanceof Number) {
            records = ((Number) recordsObj).longValue();
        }

        return DashboardSummary.builder()
                .totalAmount(amount)
                .totalQuantity(qty)
                .totalRecords(records)
                .topLocation(aggregationService.getTop("LOCATION", "amount"))
                .topProduct(aggregationService.getTop("PRODUCT", "quantity"))
                .locationWise(mapToChart(aggregationService.getAggregatedData("LOCATION", metrics, null), "SUM_AMOUNT"))
                .productWise(mapToChart(aggregationService.getAggregatedData("PRODUCT", metrics, null), "SUM_AMOUNT"))
                .dateTrend(mapToChart(aggregationService.getAggregatedData("DATE", metrics, null), "SUM_AMOUNT"))
                .build();
    }

    private List<Map<String, Object>> mapToChart(List<Map<String, Object>> data, String valueKey) {
        List<Map<String, Object>> chartData = new ArrayList<>();
        for (Map<String, Object> row : data) {
            Map<String, Object> map = new HashMap<>();
            map.put("name", row.get("key"));
            map.put("value", row.get(valueKey));
            chartData.add(map);
        }
        return chartData;
    }
}
