package com.oms.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oms.integration.dto.IntegrationDtos.LogisticsResponse;
import com.oms.integration.entity.LogisticsTracking;
import com.oms.integration.mapper.LogisticsTrackingMapper;
import com.oms.integration.provider.LogisticsProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogisticsServiceTest {

    @Mock
    private LogisticsTrackingMapper trackingMapper;

    @Mock
    private LogisticsProvider logisticsProvider;

    @InjectMocks
    private LogisticsService service;

    @Test
    void returnsStoredRecordWhenCarrierRefreshFails() {
        LogisticsTracking tracking = new LogisticsTracking();
        tracking.setId(1L);
        tracking.setOrderNo("ORDER-1");
        tracking.setTrackingNo("JT123");
        tracking.setCarrier("J&T Express SG");
        tracking.setStatus("created");
        tracking.setTraceInfo("[\"J&T shipment created\"]");
        tracking.setDeleted(0);

        when(trackingMapper.selectOne(any())).thenReturn(tracking);
        when(logisticsProvider.isConfigured()).thenReturn(true);
        when(logisticsProvider.query("JT123", "J&T Express SG"))
                .thenThrow(new IllegalStateException("provider unavailable"));

        LogisticsResponse response = service.getByOrderNo("ORDER-1");

        assertThat(response.trackingNo()).isEqualTo("JT123");
        assertThat(response.status()).isEqualTo("created");
        assertThat(response.trace()).containsExactly("J&T shipment created");
        verify(trackingMapper, never()).updateById(any(LogisticsTracking.class));
    }
}
