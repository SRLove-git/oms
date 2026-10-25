package com.oms.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oms.common.core.exception.BusinessException;
import com.oms.inventory.dto.InventoryDtos.InboundRequest;
import com.oms.inventory.dto.InventoryDtos.ImportResult;
import com.oms.inventory.entity.Sku;
import com.oms.inventory.entity.Warehouse;
import com.oms.inventory.mapper.SkuMapper;
import com.oms.inventory.mapper.WarehouseMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class InventoryImportServiceTest {

    private WarehouseMapper warehouseMapper;
    private SkuMapper skuMapper;
    private InventoryService inventoryService;
    private InventoryImportService importService;

    @BeforeEach
    void setUp() {
        warehouseMapper = mock(WarehouseMapper.class);
        skuMapper = mock(SkuMapper.class);
        inventoryService = mock(InventoryService.class);
        importService = new InventoryImportService(warehouseMapper, skuMapper, inventoryService);
    }

    @Test
    void createTemplateShouldContainExpectedSheetsAndHeaders() throws Exception {
        byte[] content = importService.createTemplate();

        assertThat(content).isNotEmpty();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            assertThat(workbook.getSheet("库存导入")).isNotNull();
            assertThat(workbook.getSheet("填写说明")).isNotNull();
            Row header = workbook.getSheet("库存导入").getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("仓库编码");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("SKU编码");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("数量");
        }
    }

    @Test
    void importFileShouldResolveCodesAndInboundEveryValidatedRow() throws Exception {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(10L);
        warehouse.setCode("WH001");
        Sku sku = new Sku();
        sku.setId(20L);
        sku.setSkuNo("SKU001");
        when(warehouseMapper.selectList(any())).thenReturn(java.util.List.of(warehouse));
        when(skuMapper.selectList(any())).thenReturn(java.util.List.of(sku));

        MockMultipartFile file = workbookFile("WH001", "SKU001", 12, "B-001", "2027-12-31", "首批入库");
        ImportResult result = importService.importFile(file, 99L);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.successRows()).isEqualTo(1);
        ArgumentCaptor<InboundRequest> request = ArgumentCaptor.forClass(InboundRequest.class);
        verify(inventoryService).inbound(request.capture(), eq(99L));
        assertThat(request.getValue().warehouseId()).isEqualTo(10L);
        assertThat(request.getValue().skuId()).isEqualTo(20L);
        assertThat(request.getValue().quantity()).isEqualTo(12);
        assertThat(request.getValue().batchNo()).isEqualTo("B-001");
        assertThat(request.getValue().expireAt()).isEqualTo(LocalDate.of(2027, 12, 31));
        assertThat(request.getValue().remark()).isEqualTo("首批入库");
    }

    @Test
    void importFileShouldRejectUnknownCodeBeforeWritingAnyInventory() throws Exception {
        when(warehouseMapper.selectList(any())).thenReturn(java.util.List.of());
        when(skuMapper.selectList(any())).thenReturn(java.util.List.of());
        MockMultipartFile file = workbookFile("UNKNOWN", "BAD-SKU", 1, "", "", "");

        assertThatThrownBy(() -> importService.importFile(file, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第 2 行")
                .hasMessageContaining("仓库编码不存在")
                .hasMessageContaining("SKU 编码不存在");
        verify(inventoryService, never()).inbound(any(), any());
    }

    @Test
    void importFileShouldRejectNonPositiveQuantity() throws Exception {
        MockMultipartFile file = workbookFile("WH001", "SKU001", 0, "", "", "");

        assertThatThrownBy(() -> importService.importFile(file, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数量必须在 1 至 1,000,000 之间");
        verify(inventoryService, never()).inbound(any(), any());
    }

    private MockMultipartFile workbookFile(
            String warehouseCode,
            String skuNo,
            int quantity,
            String batchNo,
            String expireAt,
            String remark) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("库存导入");
            Row header = sheet.createRow(0);
            String[] headers = {"仓库编码", "SKU编码", "数量", "批次号", "有效期", "备注"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(warehouseCode);
            row.createCell(1).setCellValue(skuNo);
            row.createCell(2).setCellValue(quantity);
            row.createCell(3).setCellValue(batchNo);
            row.createCell(4).setCellValue(expireAt);
            row.createCell(5).setCellValue(remark);
            workbook.write(output);
            return new MockMultipartFile(
                    "file",
                    "inventory-inbound-template.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    output.toByteArray());
        }
    }
}
