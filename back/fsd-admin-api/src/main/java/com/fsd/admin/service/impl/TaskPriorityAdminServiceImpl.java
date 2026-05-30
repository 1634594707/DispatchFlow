package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.service.TaskPriorityAdminService;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskPriorityAdminServiceImpl implements TaskPriorityAdminService {

    private final DispatchTaskMapper dispatchTaskMapper;
    private final OrderMapper orderMapper;

    public TaskPriorityAdminServiceImpl(DispatchTaskMapper dispatchTaskMapper, OrderMapper orderMapper) {
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.orderMapper = orderMapper;
    }

    @Override
    @Transactional
    public void bumpTaskPriority(Long taskId) {
        DispatchTaskEntity task = dispatchTaskMapper.selectOne(new LambdaQueryWrapper<DispatchTaskEntity>()
                .eq(DispatchTaskEntity::getId, taskId)
                .eq(DispatchTaskEntity::getDeleted, 0));
        if (task == null) {
            throw new BusinessException("DISPATCH_TASK_NOT_FOUND", "任务不存在");
        }
        OrderEntity order = orderMapper.selectById(task.getOrderId());
        if (order == null || (order.getDeleted() != null && order.getDeleted() != 0)) {
            throw new BusinessException("ORDER_NOT_FOUND", "关联订单不存在");
        }
        order.setPriority("P0");
        orderMapper.updateById(order);
    }
}
