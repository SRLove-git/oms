package com.oms.inventory.dto;

public final class WarehouseDtos {

    private WarehouseDtos() {
    }

    public record WarehouseCreateRequest(String code, String name, String address) {
    }

    public record WarehouseResponse(Long id, String code, String name, String address, Integer status) {
    }
}
