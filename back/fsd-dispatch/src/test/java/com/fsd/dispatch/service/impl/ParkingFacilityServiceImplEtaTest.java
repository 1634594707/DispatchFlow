package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.dispatch.entity.ParkingSlotEntity;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P1-2 选桩升级的闸门：eta 纯函数的功率折算与排序——"最近的桩"不再等同于"最快的桩"。
 */
class ParkingFacilityServiceImplEtaTest {

    private static ParkingSlotEntity slot(String code, double x, double y) {
        ParkingSlotEntity entity = new ParkingSlotEntity();
        entity.setSlotCode(code);
        entity.setCoordX(java.math.BigDecimal.valueOf(x));
        entity.setCoordY(java.math.BigDecimal.valueOf(y));
        return entity;
    }

    @Test
    @DisplayName("充电时长按桩功率折算：功率翻倍时长减半，功率缺失按基线")
    void chargeTimeScalesWithPilePower() {
        double base = ParkingFacilityServiceImpl.chargingEta(
                null, null, slot("A", 0, 0), null, 20, 90, 100D, 60D);
        double fast = ParkingFacilityServiceImpl.chargingEta(
                null, null, slot("A", 0, 0), 120D, 20, 90, 100D, 60D);
        double slow = ParkingFacilityServiceImpl.chargingEta(
                null, null, slot("A", 0, 0), 30D, 20, 90, 100D, 60D);
        assertEquals(base, fast * 2D, 1e-9, "120 kW 的充电时长必须是基线的一半");
        assertEquals(base, slow / 2D, 1e-9, "30 kW 的充电时长必须是基线的两倍");
        assertTrue(base > 0D);
    }

    @Test
    @DisplayName("闸门：远而快的桩可以赢过近而慢的桩——最近 ≠ 最快")
    void farFastPileBeatsNearSlowPile() {
        List<ParkingFacilityServiceImpl.ChargingCandidate> candidates = List.of(
                new ParkingFacilityServiceImpl.ChargingCandidate("NEAR-SLOW",
                        slot("NEAR-SLOW", 100, 0), 30D),
                new ParkingFacilityServiceImpl.ChargingCandidate("FAR-FAST",
                        slot("FAR-FAST", 800, 0), 120D));
        // 需要 60 个百分点：慢桩 70×100×2=14000 s，快桩 70×100×0.5=3500 s + 行驶 800/3.6≈222 s
        List<ParkingFacilityServiceImpl.ChargingCandidate> ordered =
                ParkingFacilityServiceImpl.orderChargingCandidates(candidates, null,
                        0D, 0D, 20, 90, 100D, 60D);
        assertEquals("FAR-FAST", ordered.get(0).slotCode(),
                "快桩多绕 700 px 也比慢桩先充完，必须排前面");
    }

    @Test
    @DisplayName("preferred 粘滞：已开去 A 桩的车不被排序改派")
    void preferredStaysFirst() {
        List<ParkingFacilityServiceImpl.ChargingCandidate> candidates = List.of(
                new ParkingFacilityServiceImpl.ChargingCandidate("SLOW",
                        slot("SLOW", 10, 0), 30D),
                new ParkingFacilityServiceImpl.ChargingCandidate("PREFERRED",
                        slot("PREFERRED", 900, 0), 30D));
        List<ParkingFacilityServiceImpl.ChargingCandidate> ordered =
                ParkingFacilityServiceImpl.orderChargingCandidates(candidates, "PREFERRED",
                        0D, 0D, 20, 90, 100D, 60D);
        assertEquals("PREFERRED", ordered.get(0).slotCode(),
                "preferred 必须保持首位，哪怕 eta 更差");
    }

    @Test
    @DisplayName("无起点坐标时不计行驶项，纯按充电时长排序")
    void noPositionMeansChargeTimeOnly() {
        List<ParkingFacilityServiceImpl.ChargingCandidate> candidates = List.of(
                new ParkingFacilityServiceImpl.ChargingCandidate("SLOW", slot("SLOW", 5, 5), 30D),
                new ParkingFacilityServiceImpl.ChargingCandidate("FAST", slot("FAST", 999, 999), 120D));
        List<ParkingFacilityServiceImpl.ChargingCandidate> ordered =
                ParkingFacilityServiceImpl.orderChargingCandidates(candidates, null,
                        null, null, 20, 90, 100D, 60D);
        assertEquals("FAST", ordered.get(0).slotCode());
    }
}
