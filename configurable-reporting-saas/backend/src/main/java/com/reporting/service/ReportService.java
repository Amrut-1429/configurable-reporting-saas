package com.reporting.service;

import com.reporting.dto.ReportRequestDTO;
import com.reporting.entity.RawExcelData;
import com.reporting.entity.ReportTemplate;
import com.reporting.entity.SheetRelationship;
import com.reporting.entity.UploadedFile;
import com.reporting.entity.ReportSource;
import com.reporting.repository.RawExcelDataRepository;
import com.reporting.repository.ReportTemplateRepository;
import com.reporting.repository.SheetRelationshipRepository;
import com.reporting.repository.UploadedFileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class ReportService {

    private final AggregationService aggregationService;
    private final ReportTemplateRepository reportTemplateRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final RawExcelDataRepository rawExcelDataRepository;
    private final SheetRelationshipRepository sheetRelationshipRepository;

    public ReportService(AggregationService aggregationService,
                         ReportTemplateRepository reportTemplateRepository,
                         UploadedFileRepository uploadedFileRepository,
                         RawExcelDataRepository rawExcelDataRepository,
                         SheetRelationshipRepository sheetRelationshipRepository) {
        this.aggregationService = aggregationService;
        this.reportTemplateRepository = reportTemplateRepository;
        this.uploadedFileRepository = uploadedFileRepository;
        this.rawExcelDataRepository = rawExcelDataRepository;
        this.sheetRelationshipRepository = sheetRelationshipRepository;
    }

    public Map<String, Object> generateReport(ReportRequestDTO request) {
        ReportTemplate template;
        if (request.getTemplateId() != null) {
            template = reportTemplateRepository.findById(request.getTemplateId())
                    .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        } else {
            template = new ReportTemplate();
            template.setName("Ad-hoc Report");
            template.setGroupByField(request.getGroupBy());
            template.setMetrics(request.getMetrics());
            template.setColumns(request.getColumns() != null ? request.getColumns() : request.getMetrics());
            template.setPivotRows(request.getPivotRows());
            template.setPivotColumns(request.getPivotColumns());
            template.setPivotValues(request.getPivotValues());
            template.setFormulas(request.getFormulas());
            template.setPivotFilters(request.getPivotFilters());
            template.setPivotRelationships(request.getPivotRelationships());
            template.setConditionalFormatting(request.getConditionalFormatting());
        }

        // Check if this is a Pivot Table engine request
        if ((template.getPivotRows() != null && !template.getPivotRows().isEmpty()) ||
            (template.getPivotValues() != null && !template.getPivotValues().isEmpty())) {
            return generatePivotReport(template, request);
        }

        // Fallback to original simple non-pivot engine report
        log.info("Generating fallback report for template={}", template.getName());
        Map<String, Object> response = new HashMap<>();
        response.put("templateName", template.getName());
        response.put("groupBy", template.getGroupByField() != null ? template.getGroupByField() : "OVERALL");
        response.put("columns", template.getColumns());

        // Resolve dynamic files for fallback
        Long fileId = request.getUploadedFileId();
        if (fileId == null) {
            List<UploadedFile> latestFiles = getLatestCompletedFiles();
            if (!latestFiles.isEmpty()) {
                fileId = latestFiles.get(0).getId();
            } else {
                fileId = 1L;
            }
        }

        List<Map<String, Object>> rawData = aggregationService.getAggregatedData(template, fileId, request.getFilters());
        
        List<String> metrics = template.getMetrics() != null ? template.getMetrics() : new java.util.ArrayList<>();
        List<String> columnsTemp = template.getColumns() != null ? template.getColumns() : new java.util.ArrayList<>();
        List<String> columns = new java.util.ArrayList<>(columnsTemp);
        if (columns.isEmpty()) {
            if (template.getGroupByField() != null && !"OVERALL".equalsIgnoreCase(template.getGroupByField())) {
                columns.add("key");
            }
            columns.addAll(metrics);
        } else if (!columns.contains("key") && template.getGroupByField() != null && !"OVERALL".equalsIgnoreCase(template.getGroupByField())) {
            columns.add(0, "key");
        }
        
        List<Map<String, Object>> mappedData = new java.util.ArrayList<>();
        for (Map<String, Object> rawRow : rawData) {
            Map<String, Object> mappedRow = new java.util.LinkedHashMap<>();
            int metricIdx = 0;
            for (int i = 0; i < columns.size(); i++) {
                String colName = columns.get(i);
                if (i == 0 && columns.size() > metrics.size()) {
                    mappedRow.put(colName, rawRow.get("key"));
                } else {
                    if (metricIdx < metrics.size()) {
                        String metricKey = metrics.get(metricIdx++);
                        mappedRow.put(colName, rawRow.get(metricKey));
                    }
                }
            }
            mappedData.add(mappedRow);
        }

        response.put("data", mappedData);
        return response;
    }

    public String getBaseName(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot == -1 ? filename : filename.substring(0, dot);
    }

    public List<UploadedFile> getLatestCompletedFiles() {
        return getLatestCompletedFilesForWorkspace("LATEST");
    }

    public List<UploadedFile> getLatestCompletedFilesForWorkspace(String workspace) {
        String targetWorkspace = (workspace == null || "LATEST".equalsIgnoreCase(workspace) || workspace.trim().isEmpty())
            ? "Default Workspace" : workspace;

        List<UploadedFile> allFiles = uploadedFileRepository.findAll();
        List<UploadedFile> result = new ArrayList<>();
        for (UploadedFile f : allFiles) {
            if ("COMPLETED".equals(f.getNormalizationStatus()) && targetWorkspace.equalsIgnoreCase(f.getWorkspace()) && f.getReportSource() != null) {
                result.add(f);
            }
        }
        // Sort by tableNumber ascending
        result.sort((f1, f2) -> {
            Integer t1 = f1.getReportSource().getTableNumber();
            Integer t2 = f2.getReportSource().getTableNumber();
            if (t1 != null && t2 != null) {
                return t1.compareTo(t2);
            }
            return f1.getReportSource().getId().compareTo(f2.getReportSource().getId());
        });
        return result;
    }

    private Map<String, Object> generatePivotReport(ReportTemplate template, ReportRequestDTO request) {
        log.info("Generating pivot table report for template={}", template.getName());

        String targetWorkspace = request.getWorkspace() != null ? request.getWorkspace() : template.getWorkspace();

        // 1. Resolve logical dataset (all active/latest reports in workspace)
        List<UploadedFile> latestFiles = getLatestCompletedFilesForWorkspace(targetWorkspace);
        if (latestFiles.isEmpty()) {
            Map<String, Object> emptyResponse = new HashMap<>();
            emptyResponse.put("templateName", template.getName());
            emptyResponse.put("columns", new ArrayList<>());
            emptyResponse.put("data", new ArrayList<>());
            return emptyResponse;
        }

        // 2. Fetch raw rows across all active reports (grouped/unioned by ReportSource internalKey)
        Map<String, List<Map<String, Object>>> sheetsData = new HashMap<>();
        for (UploadedFile file : latestFiles) {
            ReportSource source = file.getReportSource();
            if (source == null) continue;
            String logicalName = source.getInternalKey();

            List<RawExcelData> rows = rawExcelDataRepository.findByFileId(file.getId());
            List<Map<String, Object>> sheetRows = sheetsData.computeIfAbsent(logicalName, k -> new ArrayList<>());

            Map<String, List<Map<String, Object>>> fileSheets = new HashMap<>();
            for (RawExcelData r : rows) {
                String sheet = r.getSheetName() != null ? r.getSheetName() : "Sheet1";
                fileSheets.computeIfAbsent(sheet, k -> new ArrayList<>()).add(r.getData());
            }

            for (Map.Entry<String, List<Map<String, Object>>> entry : fileSheets.entrySet()) {
                if ("Sheet1".equalsIgnoreCase(entry.getKey()) || fileSheets.size() == 1) {
                    sheetRows.addAll(entry.getValue());
                } else {
                    String qualifiedSheetName = logicalName + "." + entry.getKey();
                    sheetsData.computeIfAbsent(qualifiedSheetName, k -> new ArrayList<>()).addAll(entry.getValue());
                }
            }
        }

        if (sheetsData.isEmpty()) {
            Map<String, Object> emptyResponse = new HashMap<>();
            emptyResponse.put("templateName", template.getName());
            emptyResponse.put("columns", new ArrayList<>());
            emptyResponse.put("data", new ArrayList<>());
            return emptyResponse;
        }

        // 3. Resolve Relationships/Joins
        List<Map<String, Object>> relations = template.getPivotRelationships();
        if (relations == null || relations.isEmpty()) {
            relations = new ArrayList<>();
            for (SheetRelationship sr : sheetRelationshipRepository.findAll()) {
                Map<String, Object> rm = new HashMap<>();
                rm.put("sourceSheet", sr.getSourceSheet());
                rm.put("sourceField", sr.getSourceField());
                rm.put("targetSheet", sr.getTargetSheet());
                rm.put("targetField", sr.getTargetField());
                rm.put("joinType", sr.getJoinType());
                relations.add(rm);
            }
        }

        List<Map<String, Object>> joinedData = performInMemoryJoins(sheetsData, relations, template);

        // 4. Apply Filters
        List<Map<String, Object>> filteredData = applyFilters(joinedData, template.getPivotFilters(), request.getFilters());

        // 5. Evaluate calculated Row Formulas (from formulas field)
        List<Map<String, Object>> enrichedData = evaluateCalculatedFields(filteredData, template.getFormulas());

        // 6. Aggregate and Build Pivot Table Grid
        Map<String, Object> pivotResult = buildPivotGrid(template, enrichedData);

        // 7. Evaluate calculated Column Formulas
        List<Map<String, Object>> finalRows = (List<Map<String, Object>>) pivotResult.get("data");
        List<Map<String, Object>> finalColumns = (List<Map<String, Object>>) pivotResult.get("columns");
        
        List<Map<String, Object>> processedRows = evaluateColumnFormulas(finalRows, finalColumns, template.getColumnFormulas());
        pivotResult.put("data", processedRows);

        return pivotResult;
    }

    private List<Map<String, Object>> evaluateColumnFormulas(List<Map<String, Object>> gridRows,
                                                             List<Map<String, Object>> gridColumns,
                                                             List<Map<String, Object>> colFormulas) {
        if (colFormulas == null || colFormulas.isEmpty() || gridRows == null || gridRows.isEmpty()) {
            return gridRows;
        }

        for (Map<String, Object> form : colFormulas) {
            String name = (String) form.get("name");
            String formula = (String) form.get("formula");
            if (name == null || formula == null || formula.trim().isEmpty()) continue;

            // Define accessor for new column
            String targetAccessor = "col_formula_" + name.replaceAll("[^a-zA-Z0-9_]", "");

            // Add new column definition to gridColumns if not exists
            boolean colExists = false;
            for (Map<String, Object> col : gridColumns) {
                if (targetAccessor.equals(col.get("accessor"))) {
                    colExists = true;
                    break;
                }
            }
            if (!colExists) {
                Map<String, Object> colDef = new HashMap<>();
                colDef.put("header", name);
                colDef.put("accessor", targetAccessor);
                // Insert right before Total columns or at the end
                int insertIdx = gridColumns.size();
                for (int i = 0; i < gridColumns.size(); i++) {
                    String h = (String) gridColumns.get(i).get("header");
                    if (h != null && h.startsWith("Total")) {
                        insertIdx = i;
                        break;
                    }
                }
                gridColumns.add(insertIdx, colDef);
            }

            // Evaluate for each row in grid
            for (Map<String, Object> row : gridRows) {
                String expr = formula;
                
                // Replace bracket placeholders [Header Name] with the cell value
                for (Map<String, Object> col : gridColumns) {
                    String header = (String) col.get("header");
                    String accessor = (String) col.get("accessor");
                    if (header == null || accessor.equals(targetAccessor)) continue;

                    String placeholder = "[" + header + "]";
                    if (expr.contains(placeholder)) {
                        Object val = row.get(accessor);
                        double numVal = 0.0;
                        if (val != null) {
                            try {
                                numVal = Double.parseDouble(val.toString());
                            } catch (NumberFormatException e) {
                                // ignore
                            }
                        }
                        expr = expr.replace(placeholder, String.valueOf(numVal));
                    }
                }

                expr = expr.replaceAll("[a-zA-Z_]", "0");
                double result = evaluateExpression(expr);
                row.put(targetAccessor, result);
            }
        }
        return gridRows;
    }

    private List<Map<String, Object>> performInMemoryJoins(Map<String, List<Map<String, Object>>> sheetsData,
                                                            List<Map<String, Object>> relations,
                                                            ReportTemplate template) {
        List<Map<String, Object>> current = new ArrayList<>();
        if (sheetsData.isEmpty()) return current;

        // Choose Root Sheet
        String rootSheet = null;
        if (relations != null && !relations.isEmpty()) {
            rootSheet = (String) relations.get(0).get("sourceSheet");
        }
        if (rootSheet == null && template != null) {
            List<String> checkFields = new ArrayList<>();
            if (template.getPivotRows() != null) checkFields.addAll(template.getPivotRows());
            if (template.getPivotColumns() != null) checkFields.addAll(template.getPivotColumns());
            if (template.getPivotValues() != null) {
                for (Map<String, Object> valMap : template.getPivotValues()) {
                    String f = (String) valMap.get("field");
                    if (f != null) checkFields.add(f);
                }
            }
            for (String f : checkFields) {
                if (f != null && f.contains(".")) {
                    String sh = f.split("\\.")[0];
                    if (sheetsData.containsKey(sh)) {
                        rootSheet = sh;
                        break;
                    }
                }
            }
        }
        if (rootSheet == null || !sheetsData.containsKey(rootSheet)) {
            rootSheet = sheetsData.keySet().iterator().next();
        }

        // Initialize with Root Sheet rows prefixed
        for (Map<String, Object> rawRow : sheetsData.get(rootSheet)) {
            Map<String, Object> row = new HashMap<>();
            for (Map.Entry<String, Object> e : rawRow.entrySet()) {
                row.put(rootSheet + "." + e.getKey(), e.getValue());
            }
            current.add(row);
        }

        if (relations == null || relations.isEmpty()) {
            return current;
        }

        // Perform joins step-by-step
        for (Map<String, Object> rel : relations) {
            String srcSheet = (String) rel.get("sourceSheet");
            String srcField = (String) rel.get("sourceField");
            String tgtSheet = (String) rel.get("targetSheet");
            String tgtField = (String) rel.get("targetField");
            String joinType = (String) rel.get("joinType"); // INNER or LEFT

            if (tgtSheet == null || !sheetsData.containsKey(tgtSheet)) continue;

            List<Map<String, Object>> targetRows = sheetsData.get(tgtSheet);
            // Group target rows by target join field
            Map<String, List<Map<String, Object>>> targetLookup = new HashMap<>();
            for (Map<String, Object> tr : targetRows) {
                String keyVal = tr.get(tgtField) != null ? tr.get(tgtField).toString().trim() : "";
                if (keyVal.startsWith("\"") && keyVal.endsWith("\"") && keyVal.length() >= 2) {
                    keyVal = keyVal.substring(1, keyVal.length() - 1).trim();
                }
                if (keyVal.isEmpty() || keyVal.equals("\"\"")) {
                    continue;
                }
                targetLookup.computeIfAbsent(keyVal.toLowerCase(), k -> new ArrayList<>()).add(tr);
            }

            List<Map<String, Object>> nextJoined = new ArrayList<>();
            String fullSrcKey = srcSheet + "." + srcField;

            for (Map<String, Object> row : current) {
                String matchVal = row.get(fullSrcKey) != null ? row.get(fullSrcKey).toString().trim() : "";
                if (matchVal.startsWith("\"") && matchVal.endsWith("\"") && matchVal.length() >= 2) {
                    matchVal = matchVal.substring(1, matchVal.length() - 1).trim();
                }
                
                List<Map<String, Object>> matches = null;
                if (!matchVal.isEmpty() && !matchVal.equals("\"\"")) {
                    matches = targetLookup.get(matchVal.toLowerCase());
                }

                if (matches != null && !matches.isEmpty()) {
                    for (Map<String, Object> matchRow : matches) {
                        Map<String, Object> joinedRow = new HashMap<>(row);
                        for (Map.Entry<String, Object> e : matchRow.entrySet()) {
                            joinedRow.put(tgtSheet + "." + e.getKey(), e.getValue());
                        }
                        nextJoined.add(joinedRow);
                    }
                } else {
                    if ("LEFT".equalsIgnoreCase(joinType)) {
                        Map<String, Object> joinedRow = new HashMap<>(row);
                        if (!targetRows.isEmpty()) {
                            for (String key : targetRows.get(0).keySet()) {
                                joinedRow.put(tgtSheet + "." + key, null);
                            }
                        }
                        nextJoined.add(joinedRow);
                    }
                }
            }
            current = nextJoined;
        }

        return current;
    }

    private List<Map<String, Object>> applyFilters(List<Map<String, Object>> joinedData,
                                                    List<Map<String, Object>> templateFilters,
                                                    Map<String, String> requestFilters) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Map<String, Object> row : joinedData) {
            boolean matches = true;

            // Apply Template filters (including Dynamic Dates)
            if (templateFilters != null) {
                for (Map<String, Object> filter : templateFilters) {
                    String field = (String) filter.get("field");
                    String operator = (String) filter.get("operator");
                    String value = (String) filter.get("value");

                    if (field == null) continue;
                    Object rowValObj = row.get(field);
                    String rowVal = rowValObj != null ? rowValObj.toString().trim() : "";

                    if ("DYNAMIC_DATE".equalsIgnoreCase(operator)) {
                        if (!checkDynamicDate(rowVal, value)) {
                            matches = false;
                            break;
                        }
                    } else if ("EQUALS".equalsIgnoreCase(operator)) {
                        if (!rowVal.equalsIgnoreCase(value)) {
                            matches = false;
                            break;
                        }
                    } else if ("CONTAINS".equalsIgnoreCase(operator)) {
                        if (!rowVal.toLowerCase().contains(value.toLowerCase())) {
                            matches = false;
                            break;
                        }
                    }
                }
            }

            // Apply Request-level overrides (fallback)
            if (matches && requestFilters != null) {
                for (Map.Entry<String, String> entry : requestFilters.entrySet()) {
                    String reqKey = entry.getKey();
                    String reqVal = entry.getValue();
                    if (reqVal == null || reqVal.isEmpty()) continue;

                    // Match dynamic columns like Location or Product
                    boolean foundMatch = false;
                    for (String key : row.keySet()) {
                        if (key.endsWith("." + reqKey) || key.equalsIgnoreCase(reqKey)) {
                            String cellVal = row.get(key) != null ? row.get(key).toString().trim() : "";
                            if (cellVal.equalsIgnoreCase(reqVal) || cellVal.toLowerCase().contains(reqVal.toLowerCase())) {
                                foundMatch = true;
                                break;
                            }
                        }
                    }
                    if (!foundMatch) {
                        matches = false;
                        break;
                    }
                }
            }

            if (matches) {
                result.add(row);
            }
        }
        return result;
    }

    private boolean checkDynamicDate(String rowValue, String rule) {
        LocalDate date = parseDate(rowValue);
        if (date == null) return false;

        LocalDate today = LocalDate.now();
        switch (rule.toUpperCase()) {
            case "TODAY":
                return date.equals(today);
            case "YESTERDAY":
                return date.equals(today.minusDays(1));
            case "LAST_7_DAYS":
                LocalDate sevenDaysAgo = today.minusDays(6);
                return !date.isBefore(sevenDaysAgo) && !date.isAfter(today);
            case "CURRENT_MONTH":
                return date.getYear() == today.getYear() && date.getMonth() == today.getMonth();
            case "PREVIOUS_MONTH":
                LocalDate firstOfCurrent = today.withDayOfMonth(1);
                LocalDate prevMonthDate = firstOfCurrent.minusMonths(1);
                return date.getYear() == prevMonthDate.getYear() && date.getMonth() == prevMonthDate.getMonth();
            case "CURRENT_YEAR":
                return date.getYear() == today.getYear();
            case "YTD":
                LocalDate firstOfYear = today.withDayOfYear(1);
                return !date.isBefore(firstOfYear) && !date.isAfter(today);
        }
        return false;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        try {
            if (dateStr.contains("T")) {
                return LocalDate.parse(dateStr.split("T")[0]);
            }
            if (dateStr.matches("^\\d{4}-\\d{2}-\\d{2}.*")) {
                return LocalDate.parse(dateStr.substring(0, 10));
            }
            if (dateStr.contains("-")) {
                String[] parts = dateStr.split("-");
                if (parts.length == 3) {
                    if (parts[2].length() == 4) {
                        return LocalDate.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
                    }
                }
            }
            if (dateStr.contains("/")) {
                String[] parts = dateStr.split("/");
                if (parts.length == 3) {
                    if (parts[2].length() == 4) {
                        return LocalDate.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
                    }
                }
            }
        } catch (Exception e) {
            // ignore parsing failures, return null
        }
        return null;
    }

    private List<Map<String, Object>> evaluateCalculatedFields(List<Map<String, Object>> data,
                                                               List<Map<String, Object>> formulas) {
        if (formulas == null || formulas.isEmpty()) return data;

        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> row : data) {
            Map<String, Object> newRow = new HashMap<>(row);
            for (Map<String, Object> form : formulas) {
                String name = (String) form.get("name");
                String formula = (String) form.get("formula");
                if (name == null || formula == null) continue;

                double val = evaluateFormula(formula, newRow);
                newRow.put(name, val);
            }
            enriched.add(newRow);
        }
        return enriched;
    }

    private double evaluateFormula(String formula, Map<String, Object> row) {
        if (formula == null || formula.trim().isEmpty()) return 0.0;
        try {
            String expr = formula;
            List<String> keys = new ArrayList<>(row.keySet());
            // Sort keys descending by length to avoid replacing prefixes
            keys.sort((k1, k2) -> Integer.compare(k2.length(), k1.length()));

            for (String key : keys) {
                if (expr.contains(key)) {
                    Object val = row.get(key);
                    double numVal = 0.0;
                    if (val != null) {
                        try {
                            numVal = Double.parseDouble(val.toString());
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                    expr = expr.replace(key, String.valueOf(numVal));
                }
            }

            // Remove any remaining alphabetical characters just in case
            expr = expr.replaceAll("[a-zA-Z_]", "0");
            return evaluateExpression(expr);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double evaluateExpression(String expression) {
        try {
            char[] tokens = expression.toCharArray();
            Stack<Double> values = new Stack<>();
            Stack<Character> ops = new Stack<>();

            for (int i = 0; i < tokens.length; i++) {
                if (tokens[i] == ' ') continue;

                if ((tokens[i] >= '0' && tokens[i] <= '9') || tokens[i] == '.') {
                    StringBuilder sbuf = new StringBuilder();
                    while (i < tokens.length && ((tokens[i] >= '0' && tokens[i] <= '9') || tokens[i] == '.')) {
                        sbuf.append(tokens[i++]);
                    }
                    i--;
                    values.push(Double.parseDouble(sbuf.toString()));
                } else if (tokens[i] == '(') {
                    ops.push(tokens[i]);
                } else if (tokens[i] == ')') {
                    while (!ops.empty() && ops.peek() != '(') {
                        values.push(applyOp(ops.pop(), values.pop(), values.pop()));
                    }
                    if (!ops.empty()) ops.pop();
                } else if (tokens[i] == '+' || tokens[i] == '-' || tokens[i] == '*' || tokens[i] == '/') {
                    while (!ops.empty() && hasPrecedence(tokens[i], ops.peek())) {
                        values.push(applyOp(ops.pop(), values.pop(), values.pop()));
                    }
                    ops.push(tokens[i]);
                }
            }

            while (!ops.empty()) {
                if (ops.peek() == '(') {
                    ops.pop();
                    continue;
                }
                values.push(applyOp(ops.pop(), values.pop(), values.pop()));
            }

            return values.empty() ? 0.0 : values.pop();
        } catch (Exception e) {
            return 0.0;
        }
    }

    private boolean hasPrecedence(char op1, char op2) {
        if (op2 == '(' || op2 == ')') return false;
        if ((op1 == '*' || op1 == '/') && (op2 == '+' || op2 == '-')) return false;
        return true;
    }

    private double applyOp(char op, double b, double a) {
        switch (op) {
            case '+': return a + b;
            case '-': return a - b;
            case '*': return a * b;
            case '/': return b == 0.0 ? 0.0 : a / b;
        }
        return 0.0;
    }

    private String formatDateIfApplicable(String valStr, String fieldName) {
        if (valStr == null) return "Unspecified";
        String lowerField = fieldName != null ? fieldName.toLowerCase() : "";
        try {
            LocalDate date = parseLocalDate(valStr);
            if (date != null) {
                if (lowerField.contains("month")) {
                    return date.format(DateTimeFormatter.ofPattern("MMM-yy"));
                }
                return date.format(DateTimeFormatter.ofPattern("dd-MMM"));
            }
        } catch (Exception e) {
            // ignore
        }
        String cleanVal = valStr.trim();
        if (cleanVal.startsWith("\"") && cleanVal.endsWith("\"") && cleanVal.length() >= 2) {
            cleanVal = cleanVal.substring(1, cleanVal.length() - 1).trim();
        }
        return cleanVal;
    }

    private Map<String, Object> buildPivotGrid(ReportTemplate template, List<Map<String, Object>> data) {
        List<String> rows = template.getPivotRows() != null ? template.getPivotRows() : new ArrayList<>();
        List<String> cols = template.getPivotColumns() != null ? template.getPivotColumns() : new ArrayList<>();
        List<Map<String, Object>> values = new ArrayList<>();
        if (template.getPivotValues() != null) {
            values.addAll(template.getPivotValues());
        }

        // UX Fallback: If columns are requested but no aggregation metrics, default to COUNT of rows
        if (values.isEmpty() && !cols.isEmpty()) {
            Map<String, Object> defaultVal = new HashMap<>();
            String defaultField = !rows.isEmpty() ? rows.get(0) : cols.get(0);
            defaultVal.put("field", defaultField);
            // Will infer aggregation automatically
            values.add(defaultVal);
        }

        // Group rows in structured keys
        // RowKey -> ColKey -> List of matching raw rows
        Map<List<String>, Map<List<String>, List<Map<String, Object>>>> grid = new LinkedHashMap<>();
        Set<List<String>> uniqueRowKeys = new LinkedHashSet<>();
        Set<List<String>> uniqueColKeys = new LinkedHashSet<>();

        for (Map<String, Object> row : data) {
            List<String> rKey = new ArrayList<>();
            for (String rField : rows) {
                Object val = row.get(rField);
                String valStr = val != null ? val.toString().trim() : "Unspecified";
                rKey.add(formatDateIfApplicable(valStr, rField));
            }
            if (rKey.isEmpty()) rKey.add("Total");

            List<String> cKey = new ArrayList<>();
            for (String cField : cols) {
                Object val = row.get(cField);
                String valStr = val != null ? val.toString().trim() : "Unspecified";
                if (isDateField(cField, valStr)) {
                    cKey.addAll(getDateGroupingParts(valStr, cField));
                } else {
                    cKey.add(formatDateIfApplicable(valStr, cField));
                }
            }

            uniqueRowKeys.add(rKey);
            if (!cKey.isEmpty() || !cols.isEmpty()) {
                uniqueColKeys.add(cKey);
            }

            grid.computeIfAbsent(rKey, k -> new LinkedHashMap<>())
                .computeIfAbsent(cKey, k -> new ArrayList<>())
                .add(row);
        }

        // Convert lists to sorted lists
        List<List<String>> sortedRowKeys = new ArrayList<>(uniqueRowKeys);
        sortedRowKeys.sort(Comparator.comparing(List::toString));

        List<List<String>> sortedColKeys = new ArrayList<>(uniqueColKeys);
        sortedColKeys.sort(this::compareColKeys);

        // Build dynamic columns list
        List<Map<String, Object>> gridColumns = new ArrayList<>();

        // 1. Add Row dimensions first
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> colDef = new HashMap<>();
            String header = rows.get(i);
            if (header.contains(".")) header = header.substring(header.indexOf(".") + 1);
            colDef.put("header", header);
            colDef.put("accessor", "row_" + i);
            gridColumns.add(colDef);
        }
        if (rows.isEmpty()) {
            Map<String, Object> colDef = new HashMap<>();
            colDef.put("header", "Metric");
            colDef.put("accessor", "row_0");
            gridColumns.add(colDef);
        }

        // 2. Add pivoted columns & metrics
        if (sortedColKeys.isEmpty()) {
            // Flat aggregations columns
            for (int v = 0; v < values.size(); v++) {
                Map<String, Object> valMap = values.get(v);
                String f = (String) valMap.get("field");
                String shortF = f != null && f.contains(".") ? f.substring(f.indexOf(".") + 1) : f;

                Map<String, Object> colDef = new HashMap<>();
                colDef.put("header", shortF);
                colDef.put("accessor", "val_" + v);
                gridColumns.add(colDef);
            }
        } else {
            // Pivoted columns
            for (List<String> cKey : sortedColKeys) {
                String colPrefix = String.join(" - ", cKey);
                for (int v = 0; v < values.size(); v++) {
                    Map<String, Object> valMap = values.get(v);
                    String f = (String) valMap.get("field");
                    String shortF = f != null && f.contains(".") ? f.substring(f.indexOf(".") + 1) : f;

                    Map<String, Object> colDef = new HashMap<>();
                    String headerText = (values.size() == 1) ? colPrefix : (colPrefix + " - " + shortF);
                    colDef.put("header", headerText);
                    
                    String accessorKey = "cell_" + String.join("_", cKey).replaceAll("[^a-zA-Z0-9_]", "") + "_" + shortF.replaceAll("[^a-zA-Z0-9_]", "");
                    colDef.put("accessor", accessorKey);
                    gridColumns.add(colDef);
                }
            }

            // 3. Add Row Grand Total columns at the end of the columns!
            for (int v = 0; v < values.size(); v++) {
                Map<String, Object> valMap = values.get(v);
                String f = (String) valMap.get("field");
                String shortF = f != null && f.contains(".") ? f.substring(f.indexOf(".") + 1) : f;

                Map<String, Object> colDef = new HashMap<>();
                String headerText = (values.size() == 1) ? "Total" : ("Total - " + shortF);
                colDef.put("header", headerText);
                colDef.put("accessor", "total_val_" + v);
                gridColumns.add(colDef);
            }
        }

        // Populate Grid Data Row Maps
        List<Map<String, Object>> gridRows = new ArrayList<>();
        for (List<String> rKey : sortedRowKeys) {
            Map<String, Object> rowMap = new LinkedHashMap<>();
            // Set Row fields
            for (int i = 0; i < rKey.size(); i++) {
                rowMap.put("row_" + i, rKey.get(i));
            }

            Map<List<String>, List<Map<String, Object>>> colDataMap = grid.get(rKey);

            if (sortedColKeys.isEmpty()) {
                List<Map<String, Object>> matchingRows = colDataMap != null ? colDataMap.get(new ArrayList<String>()) : null;
                for (int v = 0; v < values.size(); v++) {
                    Map<String, Object> valMap = values.get(v);
                    String f = (String) valMap.get("field");
                    String agg = (String) valMap.get("aggregation");
                    if (agg == null || agg.trim().isEmpty()) {
                        agg = inferAggregation(f);
                    }

                    if (matchingRows == null || matchingRows.isEmpty()) {
                        rowMap.put("val_" + v, null); // Leave blank!
                    } else {
                        double aggregatedVal = aggregateValues(matchingRows, f, agg);
                        rowMap.put("val_" + v, aggregatedVal);
                    }
                }
            } else {
                for (List<String> cKey : sortedColKeys) {
                    List<Map<String, Object>> matchingRows = colDataMap != null ? colDataMap.get(cKey) : null;

                    for (int v = 0; v < values.size(); v++) {
                        Map<String, Object> valMap = values.get(v);
                        String f = (String) valMap.get("field");
                        String agg = (String) valMap.get("aggregation");
                        if (agg == null || agg.trim().isEmpty()) {
                            agg = inferAggregation(f);
                        }
                        String shortF = f != null && f.contains(".") ? f.substring(f.indexOf(".") + 1) : f;

                        String accessorKey = "cell_" + String.join("_", cKey).replaceAll("[^a-zA-Z0-9_]", "") + "_" + shortF.replaceAll("[^a-zA-Z0-9_]", "");
                        if (matchingRows == null || matchingRows.isEmpty()) {
                            rowMap.put(accessorKey, null); // Leave blank!
                        } else {
                            double aggregatedVal = aggregateValues(matchingRows, f, agg);
                            rowMap.put(accessorKey, aggregatedVal);
                        }
                    }
                }

                // Compute Row Grand Total
                List<Map<String, Object>> rowMatchingRows = new ArrayList<>();
                if (colDataMap != null) {
                    for (List<Map<String, Object>> list : colDataMap.values()) {
                        rowMatchingRows.addAll(list);
                    }
                }
                for (int v = 0; v < values.size(); v++) {
                    Map<String, Object> valMap = values.get(v);
                    String f = (String) valMap.get("field");
                    String agg = (String) valMap.get("aggregation");
                    if (agg == null || agg.trim().isEmpty()) {
                        agg = inferAggregation(f);
                    }
                    if (rowMatchingRows.isEmpty()) {
                        rowMap.put("total_val_" + v, null);
                    } else {
                        double aggregatedVal = aggregateValues(rowMatchingRows, f, agg);
                        rowMap.put("total_val_" + v, aggregatedVal);
                    }
                }
            }
            gridRows.add(rowMap);
        }

        // 4. Compute Column Grand Total Row (at the bottom)
        if (!gridRows.isEmpty()) {
            Map<String, Object> grandTotalRow = new LinkedHashMap<>();
            for (int i = 0; i < rows.size(); i++) {
                grandTotalRow.put("row_" + i, i == 0 ? "Total" : "");
            }
            if (rows.isEmpty()) {
                grandTotalRow.put("row_0", "Total");
            }

            if (sortedColKeys.isEmpty()) {
                for (int v = 0; v < values.size(); v++) {
                    Map<String, Object> valMap = values.get(v);
                    String f = (String) valMap.get("field");
                    String agg = (String) valMap.get("aggregation");
                    if (agg == null || agg.trim().isEmpty()) {
                        agg = inferAggregation(f);
                    }
                    double aggregatedVal = aggregateValues(data, f, agg);
                    grandTotalRow.put("val_" + v, aggregatedVal);
                }
            } else {
                // Compile matching rows for each column key
                for (List<String> cKey : sortedColKeys) {
                    List<Map<String, Object>> colMatchingRows = new ArrayList<>();
                    for (Map<String, Object> row : data) {
                        boolean match = true;
                        for (int c = 0; c < cols.size(); c++) {
                            Object val = row.get(cols.get(c));
                            String valStr = val != null ? val.toString().trim() : "Unspecified";
                            String formattedVal = formatDateIfApplicable(valStr, cols.get(c));
                            if (!formattedVal.equals(cKey.get(c))) {
                                match = false;
                                break;
                            }
                        }
                        if (match) {
                            colMatchingRows.add(row);
                        }
                    }

                    for (int v = 0; v < values.size(); v++) {
                        Map<String, Object> valMap = values.get(v);
                        String f = (String) valMap.get("field");
                        String agg = (String) valMap.get("aggregation");
                        if (agg == null || agg.trim().isEmpty()) {
                            agg = inferAggregation(f);
                        }
                        String shortF = f != null && f.contains(".") ? f.substring(f.indexOf(".") + 1) : f;

                        String accessorKey = "cell_" + String.join("_", cKey).replaceAll("[^a-zA-Z0-9_]", "") + "_" + shortF.replaceAll("[^a-zA-Z0-9_]", "");
                        if (colMatchingRows.isEmpty()) {
                            grandTotalRow.put(accessorKey, null);
                        } else {
                            double aggregatedVal = aggregateValues(colMatchingRows, f, agg);
                            grandTotalRow.put(accessorKey, aggregatedVal);
                        }
                    }
                }

                // Overall grand total (bottom-right intersection)
                for (int v = 0; v < values.size(); v++) {
                    Map<String, Object> valMap = values.get(v);
                    String f = (String) valMap.get("field");
                    String agg = (String) valMap.get("aggregation");
                    if (agg == null || agg.trim().isEmpty()) {
                        agg = inferAggregation(f);
                    }
                    double aggregatedVal = aggregateValues(data, f, agg);
                    grandTotalRow.put("total_val_" + v, aggregatedVal);
                }
            }

            gridRows.add(grandTotalRow);
        }

        // Evaluate Column Formulas
        List<Map<String, Object>> colFormulas = template.getColumnFormulas();
        if (colFormulas != null && !colFormulas.isEmpty()) {
            for (Map<String, Object> cf : colFormulas) {
                String cfName = (String) cf.get("name");
                String cfFormula = (String) cf.get("formula");
                if (cfName == null || cfFormula == null || cfFormula.trim().isEmpty()) continue;

                // Add to columns
                Map<String, Object> colDef = new HashMap<>();
                colDef.put("header", cfName);
                colDef.put("accessor", "cf_" + cfName.replaceAll("[^a-zA-Z0-9_]", ""));
                gridColumns.add(colDef);

                // Compute for each row
                for (Map<String, Object> rowMap : gridRows) {
                    String expr = cfFormula;
                    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[([^\\]]+)\\]").matcher(cfFormula);
                    while (matcher.find()) {
                        String refCol = matcher.group(1);
                        String matchedAccessor = refCol;
                        for (Map<String, Object> col : gridColumns) {
                            String h = (String) col.get("header");
                            String a = (String) col.get("accessor");
                            if (refCol.equals(h) || refCol.equals(a)) {
                                matchedAccessor = a;
                                break;
                            }
                        }

                        Object cellVal = rowMap.get(matchedAccessor);
                        double cellNum = 0.0;
                        if (cellVal != null) {
                            try {
                                cellNum = Double.parseDouble(cellVal.toString());
                            } catch (NumberFormatException e) {
                                // ignore
                            }
                        }
                        expr = expr.replace("[" + refCol + "]", String.valueOf(cellNum));
                    }

                    List<Map<String, Object>> tempCols = new ArrayList<>(gridColumns);
                    tempCols.sort((c1, c2) -> Integer.compare(((String) c2.get("accessor")).length(), ((String) c1.get("accessor")).length()));
                    for (Map<String, Object> col : tempCols) {
                        String a = (String) col.get("accessor");
                        if (expr.contains(a)) {
                            Object cellVal = rowMap.get(a);
                            double cellNum = 0.0;
                            if (cellVal != null) {
                                try {
                                    cellNum = Double.parseDouble(cellVal.toString());
                                } catch (NumberFormatException e) {
                                    // ignore
                                }
                            }
                            expr = expr.replace(a, String.valueOf(cellNum));
                        }
                    }

                    double result = evaluateFormula(expr, rowMap);
                    rowMap.put("cf_" + cfName.replaceAll("[^a-zA-Z0-9_]", ""), result);
                }
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("templateName", template.getName());
        response.put("columns", gridColumns);
        response.put("data", gridRows);
        return response;
    }

    private String inferAggregation(String field) {
        if (field == null) return "COUNT";
        String fName = field.contains(".") ? field.substring(field.indexOf(".") + 1) : field;
        String lower = fName.toLowerCase().replace("_", " ").replace("-", " ").trim();

        if (lower.equals("amount") || 
            lower.equals("crm") || 
            lower.equals("quantity") || 
            lower.equals("qty") || 
            lower.equals("tax") || 
            lower.equals("labour") || 
            lower.contains("purchase amount") || 
            lower.contains("sale amount") || 
            lower.contains("tax amount") || 
            lower.contains("expense amount")) {
            return "SUM";
        }

        if (lower.contains("number") || 
            lower.contains("invoice") || 
            lower.contains("job card") || 
            lower.contains("jobcard") || 
            lower.contains("id") || 
            lower.contains("code") || 
            lower.contains("customer id") || 
            lower.contains("order number") || 
            lower.contains("invoice number") || 
            lower.contains("job card number")) {
            return "COUNT";
        }

        return "COUNT";
    }

    private double aggregateValues(List<Map<String, Object>> rows, String field, String aggregation) {
        if (rows == null || rows.isEmpty()) return 0.0;

        List<Double> doubleValues = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Object v = r.get(field);
            if (v != null) {
                try {
                    doubleValues.add(Double.parseDouble(v.toString()));
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }

        switch (aggregation.toUpperCase()) {
            case "SUM":
                double sum = 0.0;
                for (double d : doubleValues) sum += d;
                return sum;
            case "COUNT":
                return rows.size();
            case "AVG":
                if (doubleValues.isEmpty()) return 0.0;
                double avgSum = 0.0;
                for (double d : doubleValues) avgSum += d;
                return avgSum / doubleValues.size();
            case "MIN":
                if (doubleValues.isEmpty()) return 0.0;
                return Collections.min(doubleValues);
            case "MAX":
                if (doubleValues.isEmpty()) return 0.0;
                return Collections.max(doubleValues);
            case "DISTINCT_COUNT":
            case "DISTINCT COUNT":
                Set<String> uniqueVals = new HashSet<>();
                for (Map<String, Object> r : rows) {
                    Object v = r.get(field);
                    if (v != null) uniqueVals.add(v.toString().trim());
                }
                return uniqueVals.size();
        }
        return 0.0;
    }

    private static final List<String> MONTHS_ORDER = java.util.Arrays.asList(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    );

    private boolean isDateField(String fieldName, String valStr) {
        if (fieldName == null) return false;
        String cleanField = fieldName.contains(".") ? fieldName.substring(fieldName.indexOf(".") + 1) : fieldName;
        String lower = cleanField.toLowerCase();
        if (lower.contains("date") || lower.contains("dt") || lower.equals("day") || lower.equals("month") || lower.equals("year")) {
            return true;
        }
        if (valStr != null && !valStr.trim().isEmpty() && !valStr.equals("Unspecified")) {
            return parseLocalDate(valStr) != null;
        }
        return false;
    }

    private LocalDate parseLocalDate(String valStr) {
        if (valStr == null || valStr.trim().isEmpty()) return null;
        String clean = valStr.trim();
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() >= 2) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }
        if (clean.contains("T")) {
            clean = clean.substring(0, 10);
        }
        if (clean.contains(" ")) {
            clean = clean.split(" ")[0].trim();
        }
        DateTimeFormatter[] formatters = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yy"),
            DateTimeFormatter.ofPattern("M/d/yy")
        };
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(clean, formatter);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private List<String> getDateGroupingParts(String valStr, String fieldName) {
        LocalDate date = parseLocalDate(valStr);
        List<String> parts = new ArrayList<>();
        if (date != null) {
            parts.add(String.valueOf(date.getYear()));
            parts.add("Q" + ((date.getMonthValue() - 1) / 3 + 1));
            parts.add(date.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH));
            
            java.time.temporal.WeekFields weekFields = java.time.temporal.WeekFields.of(java.util.Locale.ENGLISH);
            int week = date.get(weekFields.weekOfYear());
            parts.add("Wk " + week);
            
            parts.add(date.format(DateTimeFormatter.ofPattern("dd-MMM")));
        } else {
            parts.add("Unspecified");
            parts.add("Unspecified");
            parts.add("Unspecified");
            parts.add("Unspecified");
            parts.add("Unspecified");
        }
        return parts;
    }

    private int compareColKeys(List<String> k1, List<String> k2) {
        int minSize = Math.min(k1.size(), k2.size());
        for (int i = 0; i < minSize; i++) {
            String s1 = k1.get(i);
            String s2 = k2.get(i);
            if (s1.equals(s2)) continue;
            
            if (s1.matches("^\\d{4}$") && s2.matches("^\\d{4}$")) {
                return Integer.compare(Integer.parseInt(s1), Integer.parseInt(s2));
            }
            if (s1.matches("^Q[1-4]$") && s2.matches("^Q[1-4]$")) {
                return s1.compareTo(s2);
            }
            int m1 = MONTHS_ORDER.indexOf(s1);
            int m2 = MONTHS_ORDER.indexOf(s2);
            if (m1 != -1 && m2 != -1) {
                return Integer.compare(m1, m2);
            }
            if (s1.startsWith("Wk ") && s2.startsWith("Wk ")) {
                try {
                    return Integer.compare(Integer.parseInt(s1.substring(3)), Integer.parseInt(s2.substring(3)));
                } catch (Exception e) {}
            }
            if (s1.matches("^\\d{2}-[A-Za-z]{3}$") && s2.matches("^\\d{2}-[A-Za-z]{3}$")) {
                try {
                    return Integer.compare(Integer.parseInt(s1.substring(0, 2)), Integer.parseInt(s2.substring(0, 2)));
                } catch (Exception e) {}
            }
            return s1.compareTo(s2);
        }
        return Integer.compare(k1.size(), k2.size());
    }

    public Map<String, Object> previewRelationship(Map<String, Object> rel) {
        String srcSheet = (String) rel.get("sourceSheet");
        String srcField = (String) rel.get("sourceField");
        String tgtSheet = (String) rel.get("targetSheet");
        String tgtField = (String) rel.get("targetField");

        List<UploadedFile> allFiles = uploadedFileRepository.findAll();
        List<UploadedFile> srcFiles = new ArrayList<>();
        List<UploadedFile> tgtFiles = new ArrayList<>();
        for (UploadedFile f : allFiles) {
            if ("COMPLETED".equals(f.getNormalizationStatus()) && f.getReportSource() != null) {
                if (f.getReportSource().getInternalKey().equalsIgnoreCase(srcSheet)) {
                    srcFiles.add(f);
                }
                if (f.getReportSource().getInternalKey().equalsIgnoreCase(tgtSheet)) {
                    tgtFiles.add(f);
                }
            }
        }

        List<Map<String, Object>> srcRows = new ArrayList<>();
        for (UploadedFile f : srcFiles) {
            List<RawExcelData> rows = rawExcelDataRepository.findByFileId(f.getId());
            for (RawExcelData r : rows) {
                srcRows.add(r.getData());
            }
        }

        List<Map<String, Object>> tgtRows = new ArrayList<>();
        for (UploadedFile f : tgtFiles) {
            List<RawExcelData> rows = rawExcelDataRepository.findByFileId(f.getId());
            for (RawExcelData r : rows) {
                tgtRows.add(r.getData());
            }
        }

        Map<String, List<Map<String, Object>>> targetLookup = new HashMap<>();
        for (Map<String, Object> tr : tgtRows) {
            String keyVal = tr.get(tgtField) != null ? tr.get(tgtField).toString().trim() : "";
            if (keyVal.startsWith("\"") && keyVal.endsWith("\"") && keyVal.length() >= 2) {
                keyVal = keyVal.substring(1, keyVal.length() - 1).trim();
            }
            if (keyVal.isEmpty() || keyVal.equals("\"\"")) {
                continue;
            }
            targetLookup.computeIfAbsent(keyVal.toLowerCase(), k -> new ArrayList<>()).add(tr);
        }

        int matchedCount = 0;
        int unmatchedCount = 0;
        List<Map<String, Object>> sampleData = new ArrayList<>();

        for (Map<String, Object> sr : srcRows) {
            String matchVal = sr.get(srcField) != null ? sr.get(srcField).toString().trim() : "";
            if (matchVal.startsWith("\"") && matchVal.endsWith("\"") && matchVal.length() >= 2) {
                matchVal = matchVal.substring(1, matchVal.length() - 1).trim();
            }
            if (matchVal.isEmpty() || matchVal.equals("\"\"")) {
                continue;
            }

            List<Map<String, Object>> matches = targetLookup.get(matchVal.toLowerCase());
            if (matches != null && !matches.isEmpty()) {
                matchedCount++;
                if (sampleData.size() < 10) {
                    for (Map<String, Object> matchRow : matches) {
                        if (sampleData.size() >= 10) break;
                        Map<String, Object> joinedRow = new java.util.LinkedHashMap<>();
                        for (Map.Entry<String, Object> e : sr.entrySet()) {
                            joinedRow.put(srcSheet + "." + e.getKey(), e.getValue());
                        }
                        for (Map.Entry<String, Object> e : matchRow.entrySet()) {
                            joinedRow.put(tgtSheet + "." + e.getKey(), e.getValue());
                        }
                        sampleData.add(joinedRow);
                    }
                }
            } else {
                unmatchedCount++;
                if (sampleData.size() < 10) {
                    Map<String, Object> joinedRow = new java.util.LinkedHashMap<>();
                    for (Map.Entry<String, Object> e : sr.entrySet()) {
                        joinedRow.put(srcSheet + "." + e.getKey(), e.getValue());
                    }
                    if (!tgtRows.isEmpty()) {
                        for (String key : tgtRows.get(0).keySet()) {
                            joinedRow.put(tgtSheet + "." + key, null);
                        }
                    }
                    sampleData.add(joinedRow);
                }
            }
        }

        List<String> columns = new ArrayList<>();
        if (!sampleData.isEmpty()) {
            String srcJoinKey = srcSheet + "." + srcField;
            String tgtJoinKey = tgtSheet + "." + tgtField;
            columns.add(srcJoinKey);
            columns.add(tgtJoinKey);
            int count = 2;
            
            for (String key : sampleData.get(0).keySet()) {
                if (!key.equals(srcJoinKey) && !key.equals(tgtJoinKey)) {
                    if (key.contains("\uFFFD") || key.isEmpty()) {
                        continue;
                    }
                    columns.add(key);
                    count++;
                    if (count >= 10) break;
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("matchedCount", matchedCount);
        result.put("unmatchedCount", unmatchedCount);
        result.put("columns", columns);
        result.put("sampleData", sampleData);
        return result;
    }
}
