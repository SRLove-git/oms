package com.oms.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.inventory.dto.InventoryDtos.ImportResult;
import com.oms.inventory.dto.InventoryDtos.InboundRequest;
import com.oms.inventory.entity.Sku;
import com.oms.inventory.entity.Warehouse;
import com.oms.inventory.mapper.SkuMapper;
import com.oms.inventory.mapper.WarehouseMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class InventoryImportService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_ROWS = 1000;
    private static final List<String> HEADERS = List.of("仓库编码", "SKU编码", "数量", "批次号", "有效期", "备注");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final WarehouseMapper warehouseMapper;
    private final SkuMapper skuMapper;
    private final InventoryService inventoryService;

    public InventoryImportService(
            WarehouseMapper warehouseMapper,
            SkuMapper skuMapper,
            InventoryService inventoryService) {
        this.warehouseMapper = warehouseMapper;
        this.skuMapper = skuMapper;
        this.inventoryService = inventoryService;
    }

    public byte[] createTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet inputSheet = workbook.createSheet("库存导入");
            inputSheet.createFreezePane(0, 1);
            Row headerRow = inputSheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(workbook);
            for (int i = 0; i < HEADERS.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS.get(i));
                cell.setCellStyle(headerStyle);
                inputSheet.setColumnWidth(i, switch (i) {
                    case 0, 1, 3 -> 18 * 256;
                    case 2 -> 12 * 256;
                    case 4 -> 16 * 256;
                    default -> 28 * 256;
                });
            }
            inputSheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, HEADERS.size() - 1));

            Sheet guideSheet = workbook.createSheet("填写说明");
            String[][] guideRows = {
                {"字段", "填写要求"},
                {"仓库编码", "必填。填写库存管理中的仓库编码，不是仓库 ID。"},
                {"SKU编码", "必填。填写商品管理中的 SKU 编码，不是 SKU ID。"},
                {"数量", "必填。1 至 1,000,000 的整数。"},
                {"批次号", "选填。同一仓库、SKU、批次将累加入库。"},
                {"有效期", "选填。格式为 yyyy-MM-dd；留空时默认三年后。"},
                {"备注", "选填。最多 200 个字符。"},
                {"导入规则", "最多 1,000 行、文件不超过 5 MB。任一行校验失败时整批不入库。"}
            };
            for (int i = 0; i < guideRows.length; i++) {
                Row row = guideSheet.createRow(i);
                for (int j = 0; j < guideRows[i].length; j++) {
                    Cell cell = row.createCell(j);
                    cell.setCellValue(guideRows[i][j]);
                    if (i == 0) {
                        cell.setCellStyle(headerStyle);
                    }
                }
            }
            guideSheet.setColumnWidth(0, 18 * 256);
            guideSheet.setColumnWidth(1, 72 * 256);
            guideSheet.createFreezePane(0, 1);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR.getCode(), "库存导入模板生成失败");
        }
    }

    @Transactional
    public ImportResult importFile(MultipartFile file, Long operatorId) {
        validateFile(file);
        List<ParsedRow> rows = parse(file);
        Map<String, Warehouse> warehouses = activeWarehouses();
        Map<String, Sku> skus = activeSkus();
        List<String> errors = validateRows(rows, warehouses, skus);
        if (!errors.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST.getCode(),
                    "导入校验失败：" + String.join("；", errors));
        }
        for (ParsedRow row : rows) {
            inventoryService.inbound(
                    new InboundRequest(
                            warehouses.get(row.warehouseCode()).getId(),
                            skus.get(row.skuNo()).getId(),
                            row.quantity(),
                            row.batchNo(),
                            row.expireAt(),
                            StringUtils.hasText(row.remark()) ? row.remark() : "模板批量导入"),
                    operatorId);
        }
        return new ImportResult(rows.size(), rows.size());
    }

    private List<ParsedRow> parse(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw badRequest("工作簿中没有工作表");
            }
            Sheet sheet = workbook.getSheet("库存导入");
            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }
            validateHeaders(sheet.getRow(0));
            DataFormatter formatter = new DataFormatter();
            List<ParsedRow> rows = new ArrayList<>();
            List<String> parseErrors = new ArrayList<>();
            int lastRow = Math.min(sheet.getLastRowNum(), MAX_ROWS);
            for (int rowIndex = 1; rowIndex <= lastRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isBlank(row, formatter)) {
                    continue;
                }
                try {
                    rows.add(parseRow(rowIndex + 1, row, formatter));
                } catch (IllegalArgumentException ex) {
                    parseErrors.add("第 " + (rowIndex + 1) + " 行：" + ex.getMessage());
                }
            }
            if (sheet.getLastRowNum() > MAX_ROWS) {
                parseErrors.add("数据超过 " + MAX_ROWS + " 行上限");
            }
            if (!parseErrors.isEmpty()) {
                throw badRequest("导入校验失败：" + summarizeErrors(parseErrors));
            }
            if (rows.isEmpty()) {
                throw badRequest("模板中没有可导入的数据");
            }
            return rows;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw badRequest("文件无法读取，请使用系统下载的 .xlsx 模板");
        }
    }

    private ParsedRow parseRow(int rowNumber, Row row, DataFormatter formatter) {
        String warehouseCode = text(row, 0, formatter);
        String skuNo = text(row, 1, formatter);
        String quantityText = text(row, 2, formatter);
        String batchNo = text(row, 3, formatter);
        String remark = text(row, 5, formatter);
        if (!StringUtils.hasText(warehouseCode)) {
            throw new IllegalArgumentException("仓库编码不能为空");
        }
        if (!StringUtils.hasText(skuNo)) {
            throw new IllegalArgumentException("SKU 编码不能为空");
        }
        int quantity;
        try {
            BigDecimal value = new BigDecimal(quantityText);
            quantity = value.intValueExact();
        } catch (Exception ex) {
            throw new IllegalArgumentException("数量必须是整数");
        }
        if (quantity < 1 || quantity > 1_000_000) {
            throw new IllegalArgumentException("数量必须在 1 至 1,000,000 之间");
        }
        if (batchNo.length() > 100) {
            throw new IllegalArgumentException("批次号不能超过 100 个字符");
        }
        if (remark.length() > 200) {
            throw new IllegalArgumentException("备注不能超过 200 个字符");
        }
        return new ParsedRow(
                rowNumber,
                warehouseCode,
                skuNo,
                quantity,
                batchNo,
                parseDate(row.getCell(4), formatter),
                remark);
    }

    private List<String> validateRows(
            List<ParsedRow> rows,
            Map<String, Warehouse> warehouses,
            Map<String, Sku> skus) {
        List<String> errors = new ArrayList<>();
        for (ParsedRow row : rows) {
            if (!warehouses.containsKey(row.warehouseCode())) {
                errors.add("第 " + row.rowNumber() + " 行：仓库编码不存在或已停用（" + row.warehouseCode() + "）");
            }
            if (!skus.containsKey(row.skuNo())) {
                errors.add("第 " + row.rowNumber() + " 行：SKU 编码不存在或已停用（" + row.skuNo() + "）");
            }
        }
        if (errors.size() <= 10) {
            return errors;
        }
        List<String> summarized = new ArrayList<>(errors.subList(0, 10));
        summarized.add("另有 " + (errors.size() - 10) + " 处错误");
        return summarized;
    }

    private Map<String, Warehouse> activeWarehouses() {
        Map<String, Warehouse> result = new HashMap<>();
        warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                        .eq(Warehouse::getDeleted, 0)
                        .eq(Warehouse::getStatus, 1))
                .forEach(warehouse -> result.put(warehouse.getCode(), warehouse));
        return result;
    }

    private Map<String, Sku> activeSkus() {
        Map<String, Sku> result = new HashMap<>();
        skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                        .eq(Sku::getDeleted, 0)
                        .eq(Sku::getStatus, 1))
                .forEach(sku -> result.put(sku.getSkuNo(), sku));
        return result;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("请选择要上传的 Excel 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw badRequest("文件不能超过 5 MB");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".xlsx")) {
            throw badRequest("仅支持 .xlsx 文件");
        }
    }

    private void validateHeaders(Row headerRow) {
        if (headerRow == null) {
            throw badRequest("模板表头缺失");
        }
        DataFormatter formatter = new DataFormatter();
        for (int i = 0; i < HEADERS.size(); i++) {
            String actual = text(headerRow, i, formatter);
            if (!HEADERS.get(i).equals(actual)) {
                throw badRequest("第 " + (i + 1) + " 列表头应为“" + HEADERS.get(i) + "”");
            }
        }
    }

    private LocalDate parseDate(Cell cell, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC
                && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String value = formatter.formatCellValue(cell).trim();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value, DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("有效期格式应为 yyyy-MM-dd");
        }
    }

    private boolean isBlank(Row row, DataFormatter formatter) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < HEADERS.size(); i++) {
            if (StringUtils.hasText(text(row, i, formatter))) {
                return false;
            }
        }
        return true;
    }

    private String text(Row row, int column, DataFormatter formatter) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(column);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        return style;
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST.getCode(), message);
    }

    private String summarizeErrors(List<String> errors) {
        if (errors.size() <= 10) {
            return String.join("；", errors);
        }
        return String.join("；", errors.subList(0, 10)) + "；另有 " + (errors.size() - 10) + " 处错误";
    }

    private record ParsedRow(
            int rowNumber,
            String warehouseCode,
            String skuNo,
            int quantity,
            String batchNo,
            LocalDate expireAt,
            String remark) {
    }
}
