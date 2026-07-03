package com.reporting.service;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Chunk;
import com.lowagie.text.Phrase;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.opencsv.CSVWriter;
import com.reporting.dto.ReportRequestDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ExportService {

    private final ReportService reportService;

    public ExportService(ReportService reportService) {
        this.reportService = reportService;
    }

    private void parseColumns(Map<String, Object> response, List<String> headers, List<String> accessors) {
        List<?> colsObj = (List<?>) response.get("columns");
        if (colsObj == null) return;
        for (Object col : colsObj) {
            if (col instanceof Map) {
                Map<?, ?> colMap = (Map<?, ?>) col;
                headers.add(colMap.get("header").toString());
                accessors.add(colMap.get("accessor").toString());
            } else {
                headers.add(col.toString());
                accessors.add(col.toString());
            }
        }
    }

    public byte[] exportToCsv(ReportRequestDTO request) throws Exception {
        Map<String, Object> response = reportService.generateReport(request);
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        if (data == null || data.isEmpty()) return new byte[0];

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Write UTF-8 BOM
        out.write(239);
        out.write(187);
        out.write(191);

        List<String> headers = new ArrayList<>();
        List<String> accessors = new ArrayList<>();
        parseColumns(response, headers, accessors);

        try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            writer.writeNext(headers.toArray(new String[0]));

            for (Map<String, Object> row : data) {
                String[] rowData = accessors.stream()
                        .map(acc -> {
                            Object val = row.get(acc);
                            if (val == null) return "";
                            if (val instanceof Double) {
                                return String.format(java.util.Locale.US, "%.2f", (Double) val);
                            }
                            return val.toString();
                        })
                        .toArray(String[]::new);
                writer.writeNext(rowData);
            }
        }
        return out.toByteArray();
    }

    public byte[] exportToExcel(ReportRequestDTO request) throws Exception {
        Map<String, Object> response = reportService.generateReport(request);
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        if (data == null || data.isEmpty()) return new byte[0];

        List<String> headers = new ArrayList<>();
        List<String> accessors = new ArrayList<>();
        parseColumns(response, headers, accessors);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Report");
            
            // Freeze top 5 rows
            sheet.createFreezePane(0, 5);

            // Title Style
            CellStyle titleStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleFont.setColor(IndexedColors.INDIGO.getIndex());
            titleStyle.setFont(titleFont);

            // Meta Style
            CellStyle metaStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font metaFont = workbook.createFont();
            metaFont.setItalic(true);
            metaFont.setFontHeightInPoints((short) 9);
            metaFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            metaStyle.setFont(metaFont);

            // Header Info
            Row row1 = sheet.createRow(0);
            String titleName = response.get("templateName") != null ? response.get("templateName").toString() : "Overall";
            Cell titleCell = row1.createCell(0);
            titleCell.setCellValue("📊 " + titleName.toUpperCase());
            titleCell.setCellStyle(titleStyle);
            
            int mergeCols = Math.max(4, headers.size() - 1);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, mergeCols));

            Row row2 = sheet.createRow(1);
            Cell metaCell = row2.createCell(0);
            String workspaceName = request.getWorkspace() != null ? request.getWorkspace() : "Latest Workspace";
            metaCell.setCellValue("Workspace: " + workspaceName + " | Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            metaCell.setCellStyle(metaStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, mergeCols));

            Row row3 = sheet.createRow(2);
            Cell filterCell = row3.createCell(0);
            String filtersStr = request.getFilters() != null ? request.getFilters().toString() : "None";
            filterCell.setCellValue("Filters: " + filtersStr);
            filterCell.setCellStyle(metaStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(2, 2, 0, mergeCols));
            
            // Table Headers
            Row headerRow = sheet.createRow(4); // Row 5
            
            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font poiHeaderFont = workbook.createFont();
            poiHeaderFont.setBold(true);
            poiHeaderFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(poiHeaderFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.MEDIUM);

            // Total Style
            CellStyle totalStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font totalFont = workbook.createFont();
            totalFont.setBold(true);
            totalStyle.setFont(totalFont);
            totalStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalStyle.setBorderTop(BorderStyle.DOUBLE);
            totalStyle.setBorderBottom(BorderStyle.THIN);

            // Number formatting cell styles
            CellStyle numberStyle = workbook.createCellStyle();
            numberStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            CellStyle numberTotalStyle = workbook.createCellStyle();
            numberTotalStyle.setFont(totalFont);
            numberTotalStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            numberTotalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            numberTotalStyle.setBorderTop(BorderStyle.DOUBLE);
            numberTotalStyle.setBorderBottom(BorderStyle.THIN);
            numberTotalStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            int colIdx = 0;
            for (String header : headers) {
                Cell cell = headerRow.createCell(colIdx++);
                cell.setCellValue(header.toUpperCase());
                cell.setCellStyle(headerStyle);
            }

            // Table Data
            int rowIdx = 5;
            for (int r = 0; r < data.size(); r++) {
                Map<String, Object> rowMap = data.get(r);
                Row row = sheet.createRow(rowIdx++);
                
                boolean isTotalRow = "Total".equals(rowMap.get("row_0")) || "Grand Total".equals(rowMap.get("row_0"));
                
                colIdx = 0;
                for (String acc : accessors) {
                    Cell cell = row.createCell(colIdx);
                    Object val = rowMap.get(acc);
                    
                    boolean isTotalCol = acc.startsWith("total_val_");
                    CellStyle currentStyle = isTotalRow || isTotalCol ? totalStyle : null;
                    
                    if (val != null) {
                        if (val instanceof Number) {
                            cell.setCellValue(((Number) val).doubleValue());
                            if (isTotalRow || isTotalCol) {
                                cell.setCellStyle(numberTotalStyle);
                            } else {
                                cell.setCellStyle(numberStyle);
                            }
                        } else {
                            cell.setCellValue(val.toString());
                            if (currentStyle != null) {
                                cell.setCellStyle(currentStyle);
                            }
                        }
                    } else {
                        cell.setCellValue("");
                        if (currentStyle != null) {
                            cell.setCellStyle(currentStyle);
                        }
                    }
                    colIdx++;
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public byte[] exportToPdf(ReportRequestDTO request) throws Exception {
        Map<String, Object> response = reportService.generateReport(request);
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        if (data == null || data.isEmpty()) return new byte[0];

        List<String> headers = new ArrayList<>();
        List<String> accessors = new ArrayList<>();
        parseColumns(response, headers, accessors);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(document, out);
        document.open();

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
        String titleName = response.get("templateName") != null ? response.get("templateName").toString() : "Overall";
        document.add(new Paragraph("Report Title: " + titleName, titleFont));
        document.add(new Paragraph("Generated Date: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        String filtersStr = request.getFilters() != null ? request.getFilters().toString() : "None";
        document.add(new Paragraph("Filters Applied: " + filtersStr));
        document.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(headers.size());
        table.setWidthPercentage(100);

        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header.toUpperCase(), headerFont));
            table.addCell(cell);
        }

        for (Map<String, Object> row : data) {
            for (String acc : accessors) {
                Object val = row.get(acc);
                if (val != null) {
                    if (val instanceof Double) {
                        table.addCell(String.format(java.util.Locale.US, "%.2f", (Double) val));
                    } else {
                        table.addCell(val.toString());
                    }
                } else {
                    table.addCell("");
                }
            }
        }

        document.add(table);
        document.close();
        
        return out.toByteArray();
    }
}
