package com.fsd.dispatch.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.dispatch.entity.DispatchPauseStateEntity;
import com.fsd.dispatch.mapper.DispatchPauseStateMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * 暂停派单全局开关服务（V43 / P1-10）。
 *
 * <p>统一控制自动派车、批量派车、紧急插队。
 * 当 park_id 处于暂停状态时，所有派车操作必须拒绝。
 */
@Service
@ConditionalOnExpression("${fsd.park.geo.enabled:true}")
public class DispatchPauseStateService {

    private final DispatchPauseStateMapper dispatchPauseStateMapper;

    public DispatchPauseStateService(DispatchPauseStateMapper dispatchPauseStateMapper) {
        this.dispatchPauseStateMapper = dispatchPauseStateMapper;
    }

    /**
     * 查询园区是否暂停派单。
     */
    public boolean isPaused(Long parkId) {
        if (parkId == null) {
            return false;
        }
        DispatchPauseStateEntity entity = findByParkId(parkId);
        return entity != null && entity.getIsPaused() != null && entity.getIsPaused() == 1;
    }

    /**
     * 设置园区暂停派单状态。
     *
     * @param parkId 园区ID
     * @param paused 是否暂停
     * @param reason 暂停原因
     * @param operator 操作人
     * @return 更新后的实体
     */
    public DispatchPauseStateEntity setPaused(Long parkId, boolean paused, String reason, String operator) {
        DispatchPauseStateEntity entity = findByParkId(parkId);
        if (entity == null) {
            entity = new DispatchPauseStateEntity();
            entity.setParkId(parkId);
            entity.setIsPaused(paused ? 1 : 0);
            entity.setPauseReason(reason);
            entity.setPausedBy(operator);
            entity.setPausedAt(paused ? java.time.LocalDateTime.now() : null);
            entity.setResumedAt(paused ? null : java.time.LocalDateTime.now());
            entity.setVersion(0);
            dispatchPauseStateMapper.insert(entity);
            return entity;
        }
        entity.setIsPaused(paused ? 1 : 0);
        entity.setPauseReason(reason);
        entity.setPausedBy(operator);
        entity.setPausedAt(paused ? java.time.LocalDateTime.now() : entity.getPausedAt());
        entity.setResumedAt(paused ? null : java.time.LocalDateTime.now());
        dispatchPauseStateMapper.updateById(entity);
        return entity;
    }

    /**
     * 断言园区未暂停派单，若暂停则抛出异常。
     */
    public void requireNotPaused(Long parkId, String operation) {
        if (isPaused(parkId)) {
            throw new IllegalStateException(
                    "Dispatch is paused for park " + parkId + " — " + operation + " is not allowed");
        }
    }

    private DispatchPauseStateEntity findByParkId(Long parkId) {
        LambdaQueryWrapper<DispatchPauseStateEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DispatchPauseStateEntity::getParkId, parkId)
                .last("LIMIT 1");
        return dispatchPauseStateMapper.selectOne(wrapper);
    }
}
