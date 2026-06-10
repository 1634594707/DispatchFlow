package com.fsd.admin.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.entity.AdminUserEntity;
import com.fsd.admin.mapper.AdminUserMapper;
import com.fsd.common.enums.AdminRole;
import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;
import com.fsd.dispatch.event.DispatchEventPublisher;
import com.fsd.dispatch.mapper.DispatchExceptionRecordMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OpenExceptionEscalationScheduler {

    public static final String EVENT_ESCALATED = "dispatch.exception.escalated";

    private final DispatchExceptionRecordMapper exceptionRecordMapper;
    private final AdminUserMapper adminUserMapper;
    private final DispatchEventPublisher eventPublisher;

    @Value("${fsd.alert.open-timeout-minutes:30}")
    private int openTimeoutMinutes;

    public OpenExceptionEscalationScheduler(DispatchExceptionRecordMapper exceptionRecordMapper,
                                            AdminUserMapper adminUserMapper,
                                            DispatchEventPublisher eventPublisher) {
        this.exceptionRecordMapper = exceptionRecordMapper;
        this.adminUserMapper = adminUserMapper;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${fsd.alert.escalation-check-ms:60000}")
    @Transactional
    public void escalateStaleOpenExceptions() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(openTimeoutMinutes);
        List<DispatchExceptionRecordEntity> stale = exceptionRecordMapper.selectList(
                new LambdaQueryWrapper<DispatchExceptionRecordEntity>()
                        .eq(DispatchExceptionRecordEntity::getExceptionStatus, "OPEN")
                        .isNull(DispatchExceptionRecordEntity::getEscalatedAt)
                        .lt(DispatchExceptionRecordEntity::getOccurTime, threshold));
        if (stale.isEmpty()) {
            return;
        }
        List<AdminUserEntity> admins = adminUserMapper.selectList(new LambdaQueryWrapper<AdminUserEntity>()
                .eq(AdminUserEntity::getDeleted, 0)
                .eq(AdminUserEntity::getStatus, "ACTIVE")
                .eq(AdminUserEntity::getRole, AdminRole.ADMIN.name()));
        for (DispatchExceptionRecordEntity record : stale) {
            record.setEscalatedAt(LocalDateTime.now());
            exceptionRecordMapper.updateById(record);
            Map<String, Object> payload = new HashMap<>();
            payload.put("exceptionId", record.getId());
            payload.put("exceptionType", record.getExceptionType());
            payload.put("message", record.getExceptionMsg());
            payload.put("adminCount", admins.size());
            eventPublisher.publish(EVENT_ESCALATED, String.valueOf(record.getId()), payload);
        }
    }
}
