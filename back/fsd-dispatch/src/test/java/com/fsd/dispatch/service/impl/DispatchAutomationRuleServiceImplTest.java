package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.entity.DispatchAutomationRuleEntity;
import com.fsd.dispatch.fleet.policy.FleetChargePolicy;
import com.fsd.dispatch.fleet.real.RealFleetSwapCoordinator;
import com.fsd.dispatch.fleet.simulation.SimulationMotionState;
import com.fsd.dispatch.mapper.BatterySwapCabinetMapper;
import com.fsd.dispatch.mapper.DispatchAutomationRuleMapper;
import com.fsd.dispatch.service.BatterySwapSessionService;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.DispatchStrategyRuntimeService;
import com.fsd.dispatch.service.PeakModeService;
import com.fsd.vehicle.entity.VehicleEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DispatchAutomationRuleServiceImplTest {

    @Mock
    private DispatchAutomationRuleMapper ruleMapper;
    @Mock
    private PeakModeService peakModeService;
    @Mock
    private DispatchExceptionService dispatchExceptionService;

    private DispatchStrategyRuntimeService strategyRuntimeService;
    private RealFleetSwapCoordinator realFleetSwapCoordinator;

    private DispatchAutomationRuleServiceImpl service;

    @BeforeEach
    void setUp() {
        strategyRuntimeService = mock(DispatchStrategyRuntimeService.class);
        FleetEnergyProperties chargeEnergy = new FleetEnergyProperties();
        chargeEnergy.setEnergyRecoveryMode("CHARGE");
        when(strategyRuntimeService.energyForAssign(anyLong())).thenReturn(chargeEnergy);
        realFleetSwapCoordinator = new RealFleetSwapCoordinator(
                mock(BatterySwapSessionService.class),
                mock(BatterySwapCabinetMapper.class),
                mock(FleetChargePolicy.class),
                strategyRuntimeService);
        service = new DispatchAutomationRuleServiceImpl(
                ruleMapper, peakModeService, dispatchExceptionService, realFleetSwapCoordinator);
    }

    @Test
    void evaluateFleetEnergyRulesShouldRecordChargeExceptionWhenSocBelowThreshold() {
        when(ruleMapper.selectList(ArgumentMatchers.<LambdaQueryWrapper<DispatchAutomationRuleEntity>>any()))
                .thenReturn(List.of(chargeRule("低电回充", "15")));

        VehicleEntity vehicle = vehicle(1L, 10);
        assertTrue(service.evaluateFleetEnergyRules(1L, vehicle, "STANDBY"));

        verify(dispatchExceptionService).recordVehicleException(
                eq(1L), eq("AUTO_CHARGE_REQUIRED"), anyString());
    }

    @Test
    void evaluateFleetEnergyRulesShouldPreferSwapWhenConfigured() {
        when(ruleMapper.selectList(ArgumentMatchers.<LambdaQueryWrapper<DispatchAutomationRuleEntity>>any()))
                .thenReturn(List.of(swapRule("低电换电", "20")));

        VehicleEntity vehicle = vehicle(2L, 12);
        assertTrue(service.evaluateFleetEnergyRules(1L, vehicle, "STANDBY"));

        verify(dispatchExceptionService).recordVehicleException(
                eq(2L), eq("AUTO_SWAP_REQUIRED"), anyString());
    }

    @Test
    void evaluateFleetEnergyRulesShouldSkipWhenNotIdle() {
        assertFalse(service.evaluateFleetEnergyRules(1L, vehicle(1L, 5), "EN_ROUTE"));
        verify(ruleMapper, never()).selectList(any());
    }

    @Test
    void evaluateSimulationTickShouldMoveIdleVehicleToWaitCharging() {
        when(ruleMapper.selectList(ArgumentMatchers.<LambdaQueryWrapper<DispatchAutomationRuleEntity>>any()))
                .thenReturn(List.of(chargeRule("仿真低电", "30")));

        VehicleEntity vehicle = vehicle(3L, 25);
        SimulationMotionState state = new SimulationMotionState();
        state.stage = "STANDBY";

        service.evaluateSimulationTick(1L, vehicle, state);

        assertEquals("WAIT_CHARGING", state.stage);
    }

    @Test
    void resolvePeakDistanceFactorShouldApplyBoostRule() {
        when(peakModeService.isPeakMode(1L)).thenReturn(true);
        when(ruleMapper.selectList(ArgumentMatchers.<LambdaQueryWrapper<DispatchAutomationRuleEntity>>any()))
                .thenReturn(List.of(peakBoostRule()));

        double factor = service.resolvePeakDistanceFactor(1L, 1.0);

        assertEquals(0.7, factor, 0.001);
    }

    private static VehicleEntity vehicle(long id, int soc) {
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(id);
        vehicle.setBatteryLevel(soc);
        return vehicle;
    }

    private static DispatchAutomationRuleEntity chargeRule(String name, String threshold) {
        DispatchAutomationRuleEntity rule = baseRule(name);
        rule.setConditionType("SOC_BELOW");
        rule.setConditionValue(threshold);
        rule.setActionType("CREATE_CHARGE_TASK");
        return rule;
    }

    private static DispatchAutomationRuleEntity swapRule(String name, String threshold) {
        DispatchAutomationRuleEntity rule = baseRule(name);
        rule.setConditionType("SOC_BELOW");
        rule.setConditionValue(threshold);
        rule.setActionType("CREATE_SWAP_TASK");
        return rule;
    }

    private static DispatchAutomationRuleEntity peakBoostRule() {
        DispatchAutomationRuleEntity rule = baseRule("高峰加权");
        rule.setConditionType("PEAK_MODE");
        rule.setConditionValue("PEAK");
        rule.setActionType("BOOST_DISPATCH");
        rule.setActionParamsJson("{\"weightDistanceFactor\":0.7}");
        return rule;
    }

    private static DispatchAutomationRuleEntity baseRule(String name) {
        DispatchAutomationRuleEntity rule = new DispatchAutomationRuleEntity();
        rule.setId(1L);
        rule.setParkId(1L);
        rule.setRuleName(name);
        rule.setEnabled(1);
        rule.setDeleted(0);
        return rule;
    }
}
