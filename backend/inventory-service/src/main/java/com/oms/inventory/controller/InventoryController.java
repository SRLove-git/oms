package com.oms.inventory.controller;

import com.oms.common.core.result.PageResult;
import com.oms.common.core.result.Result;
import com.oms.inventory.dto.InventoryDtos.InboundRequest;
import com.oms.inventory.dto.InventoryDtos.ImportResult;
import com.oms.inventory.dto.InventoryDtos.InventoryAlertResponse;
import com.oms.inventory.dto.InventoryDtos.TraceResponse;
import com.oms.inventory.dto.InventoryDtos.InventoryResponse;
import com.oms.inventory.dto.InventoryDtos.ReserveRequest;
import com.oms.inventory.service.InventoryService;
import com.oms.inventory.service.InventoryImportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/v1/inventories")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryImportService inventoryImportService;

    public InventoryController(InventoryService inventoryService, InventoryImportService inventoryImportService) {
        this.inventoryService = inventoryService;
        this.inventoryImportService = inventoryImportService;
    }

    @PostMapping("/inbound")
    public Result<Void> inbound(
            @RequestBody InboundRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId) {
        inventoryService.inbound(request, operatorId);
        return Result.ok();
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> downloadImportTemplate() {
        byte[] content = inventoryImportService.createTemplate();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("inventory-inbound-template.xlsx")
                                .build()
                                .toString())
                .body(content);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ImportResult> importInventory(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId) {
        return Result.ok(inventoryImportService.importFile(file, operatorId));
    }

    @PostMapping("/reserve")
    public Result<Void> reserve(
            @RequestBody ReserveRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId) {
        inventoryService.reserve(request, operatorId);
        return Result.ok();
    }

    @PostMapping("/release")
    public Result<Void> release(
            @RequestBody ReserveRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId) {
        inventoryService.release(request, operatorId);
        return Result.ok();
    }

    @PostMapping("/deduct")
    public Result<Void> deduct(
            @RequestBody ReserveRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId) {
        inventoryService.deduct(request, operatorId);
        return Result.ok();
    }

    @PostMapping("/restore")
    public Result<Void> restore(
            @RequestBody ReserveRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long operatorId) {
        inventoryService.restore(request, operatorId);
        return Result.ok();
    }

    @GetMapping
    public Result<PageResult<InventoryResponse>> page(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long skuId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(inventoryService.page(warehouseId, skuId, page, size));
    }

    @GetMapping("/alerts")
    public Result<List<InventoryAlertResponse>> alerts() {
        return Result.ok(inventoryService.alerts());
    }

    @GetMapping("/trace")
    public Result<TraceResponse> trace(@RequestParam String udi) {
        return Result.ok(inventoryService.traceByUdi(udi));
    }
}
