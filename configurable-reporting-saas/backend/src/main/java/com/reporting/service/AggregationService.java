package com.reporting.service;

import com.reporting.entity.Transaction;
import com.reporting.entity.Location;
import com.reporting.entity.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AggregationService {
    private final EntityManager entityManager;

    public AggregationService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Map<String, Object> getTotals() {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Transaction> root = query.from(Transaction.class);
        
        query.multiselect(
                cb.count(root.get("id")).alias("totalRecords"),
                cb.sum(root.get("saleAmount")).alias("totalAmount"),
                cb.sum(root.get("quantity")).alias("totalQuantity")
        );
        Tuple totals = entityManager.createQuery(query).getSingleResult();
        
        Map<String, Object> map = new HashMap<>();
        map.put("totalAmount", totals.get("totalAmount") != null ? totals.get("totalAmount") : BigDecimal.ZERO);
        map.put("totalQuantity", totals.get("totalQuantity") != null ? totals.get("totalQuantity") : BigDecimal.ZERO);
        map.put("totalRecords", totals.get("totalRecords") != null ? totals.get("totalRecords") : 0L);
        return map;
    }

    public List<Map<String, Object>> getAggregatedData(com.reporting.entity.ReportTemplate template, Long fileId, Map<String, String> filters) {
        String groupBy = template.getGroupByField();
        if (groupBy == null || groupBy.isEmpty() || groupBy.equalsIgnoreCase("OVERALL")) {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Tuple> query = cb.createTupleQuery();
            Root<Transaction> root = query.from(Transaction.class);
            List<Predicate> predicates = new ArrayList<>();
            if (fileId != null) {
                predicates.add(cb.equal(root.get("file").get("id"), fileId));
            }
            if (filters != null) {
                if (filters.containsKey("startDate") && filters.get("startDate") != null && !filters.get("startDate").isEmpty()) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), java.time.LocalDate.parse(filters.get("startDate"))));
                }
                if (filters.containsKey("endDate") && filters.get("endDate") != null && !filters.get("endDate").isEmpty()) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), java.time.LocalDate.parse(filters.get("endDate"))));
                }
                if (filters.containsKey("location") && filters.get("location") != null && !filters.get("location").isEmpty()) {
                    Join<Transaction, Location> locationJoin = root.join("location", JoinType.INNER);
                    predicates.add(cb.equal(locationJoin.get("name"), filters.get("location")));
                }
                if (filters.containsKey("product") && filters.get("product") != null && !filters.get("product").isEmpty()) {
                    Join<Transaction, Product> productJoin = root.join("product", JoinType.INNER);
                    predicates.add(cb.equal(productJoin.get("partNumber"), filters.get("product")));
                }
            }
            if (!predicates.isEmpty()) {
                query.where(cb.and(predicates.toArray(new Predicate[0])));
            }

            List<Selection<?>> selections = new ArrayList<>();
            List<String> metrics = template.getMetrics();
            if (metrics != null) {
                if (metrics.contains("COUNT_TRANSACTIONS")) selections.add(cb.count(root.get("id")).alias("COUNT_TRANSACTIONS"));
                if (metrics.contains("COUNT_INVOICES")) selections.add(cb.countDistinct(root.get("invoiceNumber")).alias("COUNT_INVOICES"));
                if (metrics.contains("SUM_AMOUNT")) selections.add(cb.sum(root.get("saleAmount")).alias("SUM_AMOUNT"));
                if (metrics.contains("SUM_QUANTITY")) selections.add(cb.sum(root.get("quantity")).alias("SUM_QUANTITY"));
                if (metrics.contains("SUM_TAX")) selections.add(cb.sum(root.get("taxAmount")).alias("SUM_TAX"));
            }
            query.multiselect(selections);
            
            if (selections.isEmpty()) return List.of();
            
            Tuple totals = entityManager.createQuery(query).getSingleResult();
            
            Map<String, Object> map = new HashMap<>();
            map.put("key", "OVERALL");
            if (metrics != null) {
                if (metrics.contains("COUNT_TRANSACTIONS")) map.put("COUNT_TRANSACTIONS", totals.get("COUNT_TRANSACTIONS") != null ? ((Number) totals.get("COUNT_TRANSACTIONS")).longValue() : 0L);
                if (metrics.contains("COUNT_INVOICES")) map.put("COUNT_INVOICES", totals.get("COUNT_INVOICES") != null ? ((Number) totals.get("COUNT_INVOICES")).longValue() : 0L);
                if (metrics.contains("SUM_AMOUNT")) map.put("SUM_AMOUNT", totals.get("SUM_AMOUNT") != null ? ((Number) totals.get("SUM_AMOUNT")).doubleValue() : 0.0);
                if (metrics.contains("SUM_QUANTITY")) map.put("SUM_QUANTITY", totals.get("SUM_QUANTITY") != null ? ((Number) totals.get("SUM_QUANTITY")).doubleValue() : 0.0);
                if (metrics.contains("SUM_TAX")) map.put("SUM_TAX", totals.get("SUM_TAX") != null ? ((Number) totals.get("SUM_TAX")).doubleValue() : 0.0);
            }
            return List.of(map);
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Transaction> root = query.from(Transaction.class);
        
        Expression<?> groupByExpr = null;
        Expression<?> selectKey = null;
        
        Join<Transaction, Location> locationJoin = null;
        Join<Transaction, Product> productJoin = null;

        switch (groupBy.toUpperCase()) {
            case "LOCATION":
                locationJoin = root.join("location", JoinType.LEFT);
                groupByExpr = locationJoin.get("name");
                selectKey = groupByExpr;
                break;
            case "PRODUCT":
                productJoin = root.join("product", JoinType.LEFT);
                groupByExpr = productJoin.get("partNumber");
                selectKey = groupByExpr;
                break;
            case "DATE":
                groupByExpr = root.get("transactionDate");
                selectKey = groupByExpr;
                break;
            case "DAY":
                groupByExpr = cb.function("date_part", Integer.class, cb.literal("day"), root.get("transactionDate"));
                selectKey = groupByExpr;
                break;
            case "MONTH":
                groupByExpr = cb.function("date_part", Integer.class, cb.literal("month"), root.get("transactionDate"));
                selectKey = groupByExpr;
                break;
            default:
                throw new IllegalArgumentException("Unsupported groupBy: " + groupBy);
        }

        List<Predicate> predicates = new ArrayList<>();
        if (fileId != null) {
            predicates.add(cb.equal(root.get("file").get("id"), fileId));
        }
        if (filters != null) {
            if (filters.containsKey("startDate") && filters.get("startDate") != null && !filters.get("startDate").isEmpty()) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), java.time.LocalDate.parse(filters.get("startDate"))));
            }
            if (filters.containsKey("endDate") && filters.get("endDate") != null && !filters.get("endDate").isEmpty()) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), java.time.LocalDate.parse(filters.get("endDate"))));
            }
            if (filters.containsKey("location") && filters.get("location") != null && !filters.get("location").isEmpty()) {
                if (locationJoin == null) locationJoin = root.join("location", JoinType.INNER);
                predicates.add(cb.equal(locationJoin.get("name"), filters.get("location")));
            }
            if (filters.containsKey("product") && filters.get("product") != null && !filters.get("product").isEmpty()) {
                if (productJoin == null) productJoin = root.join("product", JoinType.INNER);
                predicates.add(cb.equal(productJoin.get("partNumber"), filters.get("product")));
            }
        }
        if (!predicates.isEmpty()) {
            query.where(cb.and(predicates.toArray(new Predicate[0])));
        }

        List<Selection<?>> selections = new ArrayList<>();
        selections.add(selectKey.alias("key"));
        
        List<String> metrics = template.getMetrics();
        if (metrics != null) {
            if (metrics.contains("COUNT_TRANSACTIONS")) selections.add(cb.count(root.get("id")).alias("COUNT_TRANSACTIONS"));
            if (metrics.contains("COUNT_INVOICES")) selections.add(cb.countDistinct(root.get("invoiceNumber")).alias("COUNT_INVOICES"));
            if (metrics.contains("SUM_AMOUNT")) selections.add(cb.sum(root.get("saleAmount")).alias("SUM_AMOUNT"));
            if (metrics.contains("SUM_QUANTITY")) selections.add(cb.sum(root.get("quantity")).alias("SUM_QUANTITY"));
            if (metrics.contains("SUM_TAX")) selections.add(cb.sum(root.get("taxAmount")).alias("SUM_TAX"));
        }

        query.multiselect(selections);
        query.groupBy(groupByExpr);
        query.orderBy(cb.asc(groupByExpr));

        List<Tuple> results = entityManager.createQuery(query).getResultList();
        List<Map<String, Object>> chartData = new ArrayList<>();
        for (Tuple t : results) {
            Map<String, Object> map = new HashMap<>();
            map.put("key", t.get("key") != null ? t.get("key").toString() : "Unmapped");
            if (metrics != null) {
                if (metrics.contains("COUNT_TRANSACTIONS")) map.put("COUNT_TRANSACTIONS", t.get("COUNT_TRANSACTIONS") != null ? ((Number) t.get("COUNT_TRANSACTIONS")).longValue() : 0L);
                if (metrics.contains("COUNT_INVOICES")) map.put("COUNT_INVOICES", t.get("COUNT_INVOICES") != null ? ((Number) t.get("COUNT_INVOICES")).longValue() : 0L);
                if (metrics.contains("SUM_AMOUNT")) map.put("SUM_AMOUNT", t.get("SUM_AMOUNT") != null ? ((Number) t.get("SUM_AMOUNT")).doubleValue() : 0.0);
                if (metrics.contains("SUM_QUANTITY")) map.put("SUM_QUANTITY", t.get("SUM_QUANTITY") != null ? ((Number) t.get("SUM_QUANTITY")).doubleValue() : 0.0);
                if (metrics.contains("SUM_TAX")) map.put("SUM_TAX", t.get("SUM_TAX") != null ? ((Number) t.get("SUM_TAX")).doubleValue() : 0.0);
            }
            chartData.add(map);
        }
        return chartData;
    }

    public List<Map<String, Object>> getAggregatedData(String groupBy, List<String> metrics, Map<String, String> filters) {
        com.reporting.entity.ReportTemplate dummy = new com.reporting.entity.ReportTemplate();
        dummy.setGroupByField(groupBy);
        dummy.setMetrics(metrics);
        return getAggregatedData(dummy, null, filters);
    }

    public String getTop(String groupBy, String orderByMetric) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Transaction> root = query.from(Transaction.class);

        Path<?> groupByPath = null;
        if (groupBy.equalsIgnoreCase("LOCATION")) {
            Join<Transaction, Location> locationJoin = root.join("location", JoinType.LEFT);
            groupByPath = locationJoin.get("name");
        } else if (groupBy.equalsIgnoreCase("PRODUCT")) {
            Join<Transaction, Product> productJoin = root.join("product", JoinType.LEFT);
            groupByPath = productJoin.get("partNumber");
        }

        Expression<?> orderExpr = cb.count(root.get("id"));
        if (orderByMetric != null && orderByMetric.equalsIgnoreCase("AMOUNT")) {
            orderExpr = cb.sum(root.get("saleAmount"));
        }

        query.multiselect(groupByPath.alias("key"))
             .groupBy(groupByPath)
             .orderBy(cb.desc(orderExpr));

        List<Tuple> results = entityManager.createQuery(query).setMaxResults(1).getResultList();
        return results.isEmpty() || results.get(0).get("key") == null ? "N/A" : results.get(0).get("key").toString();
    }
}
