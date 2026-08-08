package com.oms.inventory.controller;

import com.oms.common.core.result.Result;
import com.oms.inventory.dto.SpuDtos.SpuCreateRequest;
import com.oms.inventory.dto.SpuDtos.SpuResponse;
import com.oms.inventory.service.SpuService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spus")
public class SpuController {

    private final SpuService spuService;

    public SpuController(SpuService spuService) {
        this.spuService = spuService;
    }

    @PostMapping
    public Result<Long> create(@RequestBody SpuCreateRequest request) {
        return Result.ok(spuService.create(request));
    }

    @GetMapping
    public Result<List<SpuResponse>> list() {
        return Result.ok(spuService.list());
    }
}
