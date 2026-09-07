package com.oms.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.oms.common.core.exception.BusinessException;
import com.oms.common.core.result.ErrorCode;
import com.oms.inventory.dto.InventoryDtos.StockTakeItemRequest;
import com.oms.inventory.dto.InventoryDtos.StockTakeItemResponse;
import com.oms.inventory.dto.InventoryDtos.StockTakeRequest;
import com.oms.inventory.dto.InventoryDtos.StockTakeResponse;
import com.oms.inventory.dto.InventoryDtos.WaveCreateRequest;
import com.oms.inventory.dto.InventoryDtos.WaveResponse;
import com.oms.inventory.dto.InventoryDtos.WaveStatusRequest;
import com.oms.inventory.entity.Inventory;
import com.oms.inventory.entity.PickWave;
import com.oms.inventory.entity.StockTake;
import com.oms.inventory.entity.StockTakeItem;
import com.oms.inventory.mapper.InventoryMapper;
import com.oms.inventory.mapper.PickWaveMapper;
import com.oms.inventory.mapper.StockTakeItemMapper;
import com.oms.inventory.mapper.StockTakeMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockTakeService {

    private final StockTakeMapper takeMapper;
    private final StockTakeItemMapper itemMapper;
    private final InventoryMapper inventoryMapper;
    private final PickWaveMapper waveMapper;

    public StockTakeService(
            StockTakeMapper takeMapper,
            StockTakeItemMapper itemMapper,
            InventoryMapper inventoryMapper,
            PickWaveMapper waveMapper) {
        this.takeMapper = takeMapper;
        this.itemMapper = itemMapper;
        this.inventoryMapper = inventoryMapper;
        this.waveMapper = waveMapper;
    }

    @Transactional
    public StockTakeResponse create(StockTakeRequest request, Long operatorId) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "盘点明细不能为空");
        }
        StockTake take = new StockTake();
        take.setTakeNo("ST" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ThreadLocalRandom.current().nextInt(100, 1000));
        take.setWarehouseId(request.warehouseId());
        take.setStatus(0);
        take.setRemark(request.remark());
        take.setOperatorId(operatorId);
        takeMapper.insert(take);
        for (StockTakeItemRequest item : request.items()) {
            StockTakeItem row = new StockTakeItem();
            row.setTakeId(take.getId());
            row.setSkuId(item.skuId());
            row.setExpectedQuantity(item.expectedQuantity());
            row.setActualQuantity(item.actualQuantity());
            itemMapper.insert(row);
        }
        return get(take.getId());
    }

    public StockTakeResponse get(Long id) {
        StockTake take = takeMapper.selectById(id);
        if (take == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        List<StockTakeItem> items = itemMapper.selectList(new LambdaQueryWrapper<StockTakeItem>()
                .eq(StockTakeItem::getTakeId, id));
        return new StockTakeResponse(
                take.getId(),
                take.getTakeNo(),
                take.getWarehouseId(),
                take.getStatus(),
                take.getRemark(),
                items.stream().map(this::toItem).toList());
    }

    public List<StockTakeResponse> list(Long warehouseId) {
        LambdaQueryWrapper<StockTake> wrapper = new LambdaQueryWrapper<StockTake>().orderByDesc(StockTake::getId);
        if (warehouseId != null) {
            wrapper.eq(StockTake::getWarehouseId, warehouseId);
        }
        return takeMapper.selectList(wrapper).stream().map(take -> get(take.getId())).toList();
    }

    @Transactional
    public void review(Long id) {
        StockTake take = takeMapper.selectById(id);
        if (take == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        List<StockTakeItem> items = itemMapper.selectList(new LambdaQueryWrapper<StockTakeItem>()
                .eq(StockTakeItem::getTakeId, id));
        for (StockTakeItem item : items) {
            int diff = item.getActualQuantity() == null ? 0 : item.getActualQuantity() - item.getExpectedQuantity();
            if (diff != 0) {
                inventoryMapper.update(null, new LambdaUpdateWrapper<Inventory>()
                        .eq(Inventory::getWarehouseId, take.getWarehouseId())
                        .eq(Inventory::getSkuId, item.getSkuId())
                        .setSql("quantity = quantity + " + diff));
            }
        }
        take.setStatus(2);
        takeMapper.updateById(take);
    }

    @Transactional
    public WaveResponse createWave(WaveCreateRequest request, Long operatorId) {
        PickWave wave = new PickWave();
        wave.setWaveNo("W" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ThreadLocalRandom.current().nextInt(100, 1000));
        wave.setOrderNo(request.orderNo());
        wave.setSkuId(request.skuId());
        wave.setQuantity(request.quantity() == null ? 1 : request.quantity());
        wave.setStatus(0);
        wave.setOperatorId(operatorId);
        waveMapper.insert(wave);
        return toWave(wave);
    }

    @Transactional
    public WaveResponse updateWave(Long id, WaveStatusRequest request) {
        PickWave wave = waveMapper.selectById(id);
        if (wave == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        wave.setStatus(request.status());
        waveMapper.updateById(wave);
        return toWave(wave);
    }

    public List<WaveResponse> listWaves() {
        return waveMapper.selectList(new LambdaQueryWrapper<PickWave>().orderByDesc(PickWave::getId))
                .stream().map(this::toWave).toList();
    }

    private StockTakeItemResponse toItem(StockTakeItem item) {
        return new StockTakeItemResponse(
                item.getId(), item.getTakeId(), item.getSkuId(),
                item.getExpectedQuantity(), item.getActualQuantity());
    }

    private WaveResponse toWave(PickWave wave) {
        return new WaveResponse(
                wave.getId(), wave.getWaveNo(), wave.getOrderNo(),
                wave.getSkuId(), wave.getQuantity(), wave.getStatus());
    }
}
