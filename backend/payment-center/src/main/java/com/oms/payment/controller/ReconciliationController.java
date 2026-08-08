package com.oms.payment.controller;

import com.oms.common.core.result.PageResult;
import com.oms.common.core.result.Result;
import com.oms.payment.dto.ReconciliationDtos.ReconciliationResponse;
import com.oms.payment.dto.ReconciliationDtos.RunRequest;
import com.oms.payment.service.ReconciliationService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/run")
    public Result<ReconciliationResponse> run(@RequestBody(required = false) RunRequest request) {
        RunRequest req = request == null ? new RunRequest(null, null, false) : request;
        return Result.ok(reconciliationService.run(req.bizDate(), req.channel(), req.simulateDiff()));
    }

    @GetMapping
    public Result<PageResult<ReconciliationResponse>> page(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bizDate,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(reconciliationService.page(bizDate, channel, status, page, size));
    }

    @PostMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id) {
        reconciliationService.handle(id);
        return Result.ok();
    }
}
