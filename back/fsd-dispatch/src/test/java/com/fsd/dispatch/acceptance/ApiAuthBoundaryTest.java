package com.fsd.dispatch.acceptance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.dto.VehicleTelemetryRequest;
import com.fsd.dispatch.entity.ExternalApiKeyEntity;
import com.fsd.dispatch.fleet.FleetAdapterRegistry;
import com.fsd.dispatch.infra.VehicleTelemetryIdempotencyService;
import com.fsd.dispatch.mapper.ExternalApiKeyMapper;
import com.fsd.dispatch.service.impl.MobileOrderAuthServiceImpl;
import com.fsd.dispatch.service.impl.VehicleGatewayServiceImpl;
import com.fsd.vehicle.entity.VehicleEntity;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 路线图 Phase 4：接口认证边界验证。
 *
 * <ul>
 *   <li>移动下单：缺少/无效 X-Mobile-Api-Key 一律拒绝；</li>
 *   <li>车辆网关：仅 REAL 链路车辆可上报，SIM 配置拒绝（未知编码由空指针防护拒绝）；</li>
 *   <li>管理端 token 边界由 AdminAuthInterceptor + 各控制器测试覆盖；</li>
 *   <li>SSE ticket 一次性消费边界由 AdminSseTicketServiceImplTest 覆盖。</li>
 * </ul>
 */
class ApiAuthBoundaryTest {

    private ExternalApiKeyMapper apiKeyMapper;
    private MobileOrderAuthServiceImpl mobileOrderAuthService;

    @BeforeEach
    void setUp() {
        apiKeyMapper = mock(ExternalApiKeyMapper.class);
        mobileOrderAuthService = new MobileOrderAuthServiceImpl(apiKeyMapper);
        ReflectionTestUtils.setField(mobileOrderAuthService, "requireApiKey", true);
    }

    @Test
    void mobileOrderWithoutKeyShouldBeRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> mobileOrderAuthService.validateMobileOrderKey(null));
        assertEquals("MOBILE_ORDER_KEY_REQUIRED", ex.getCode());
    }

    @Test
    void mobileOrderWithInvalidKeyShouldBeRejected() {
        when(apiKeyMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> mobileOrderAuthService.validateMobileOrderKey("bad-key"));
        assertEquals("MOBILE_ORDER_KEY_INVALID", ex.getCode());
    }

    @Test
    void mobileOrderRateLimitShouldRejectBurst() {
        when(apiKeyMapper.selectOne(any())).thenReturn(activeKey());
        String key = "rate-limit-key";
        // 默认限流 30 次/分钟：第 31 次必须被拒
        for (int i = 0; i < 30; i++) {
            mobileOrderAuthService.validateMobileOrderKey(key);
        }
        BusinessException ex = assertThrows(BusinessException.class,
                () -> mobileOrderAuthService.validateMobileOrderKey(key));
        assertEquals("MOBILE_ORDER_RATE_LIMIT", ex.getCode());
    }

    @Test
    void gatewayShouldRejectSimVehicleTelemetry() {
        com.fsd.vehicle.service.VehicleService vehicleService =
                mock(com.fsd.vehicle.service.VehicleService.class);
        VehicleEntity simVehicle = simVehicle();
        VehicleTelemetryIdempotencyService idempotencyService =
                mock(VehicleTelemetryIdempotencyService.class);
        when(idempotencyService.markIfFirstTelemetry(any())).thenReturn(true);
        when(vehicleService.getByVehicleCode("SIM-001")).thenReturn(simVehicle);
        VehicleGatewayServiceImpl gateway = new VehicleGatewayServiceImpl(
                vehicleService,
                mock(com.fsd.vehicle.service.VehicleReportService.class),
                mock(FleetAdapterRegistry.class),
                idempotencyService);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> gateway.ingestTelemetry(telemetryRequest()));
        assertEquals("VEHICLE_NOT_REAL", ex.getCode());
    }

    private ExternalApiKeyEntity activeKey() {
        ExternalApiKeyEntity entity = new ExternalApiKeyEntity();
        entity.setId(1L);
        entity.setApiKey("rate-limit-key");
        entity.setStatus("ACTIVE");
        entity.setDeleted(0);
        return entity;
    }

    private VehicleEntity simVehicle() {
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(2L);
        vehicle.setVehicleCode("SIM-001");
        vehicle.setLinkMode(VehicleLinkMode.SIM.name());
        return vehicle;
    }

    private VehicleTelemetryRequest telemetryRequest() {
        VehicleTelemetryRequest request = new VehicleTelemetryRequest();
        request.setVehicleCode("SIM-001");
        request.setRuntimeStage("STANDBY");
        request.setPluggedIn(false);
        request.setSoc(95);
        request.setX(BigDecimal.valueOf(600));
        request.setY(BigDecimal.valueOf(400));
        request.setReportTime(java.time.LocalDateTime.now());
        request.setEventSeq(1L);
        return request;
    }
}