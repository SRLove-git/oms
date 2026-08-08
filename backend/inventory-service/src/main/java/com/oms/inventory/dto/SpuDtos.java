package com.oms.inventory.dto;

public final class SpuDtos {

    private SpuDtos() {
    }

    public record SpuCreateRequest(String spuNo, String name, Long categoryId, String brand) {
    }

    public record SpuResponse(Long id, String spuNo, String name, String brand, Integer status) {
    }
}
