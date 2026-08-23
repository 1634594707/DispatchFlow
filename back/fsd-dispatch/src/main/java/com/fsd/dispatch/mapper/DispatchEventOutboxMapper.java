package com.fsd.dispatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fsd.dispatch.entity.DispatchEventOutboxEntity;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface DispatchEventOutboxMapper extends BaseMapper<DispatchEventOutboxEntity> {

    @Update("UPDATE t_dispatch_event_outbox "
            + "SET status = 'PROCESSING', claim_token = #{token}, claimed_at = #{claimedAt}, lease_until = #{leaseUntil} "
            + "WHERE id = #{id} "
            + "AND ((status IN ('PENDING','FAILED') AND next_retry_time <= #{now}) "
            + "OR (status = 'PROCESSING' AND lease_until <= #{now}))")
    int claimIfAvailable(@Param("id") Long id,
                         @Param("token") String token,
                         @Param("now") LocalDateTime now,
                         @Param("claimedAt") LocalDateTime claimedAt,
                         @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("UPDATE t_dispatch_event_outbox "
            + "SET status = 'PROCESSING', claim_token = #{token}, claimed_at = #{claimedAt}, lease_until = #{leaseUntil} "
            + "WHERE event_id = #{eventId} "
            + "AND status IN ('PENDING','FAILED') AND next_retry_time <= #{now} "
            + "AND (claim_token IS NULL OR lease_until IS NULL OR lease_until <= #{now})")
    int claimEventIfAvailable(@Param("eventId") String eventId,
                              @Param("token") String token,
                              @Param("now") LocalDateTime now,
                              @Param("claimedAt") LocalDateTime claimedAt,
                              @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("UPDATE t_dispatch_event_outbox "
            + "SET status = 'PUBLISHED', last_error = NULL, next_retry_time = NULL, "
            + "claim_token = NULL, claimed_at = NULL, lease_until = NULL "
            + "WHERE event_id = #{eventId} AND status = 'PROCESSING' AND claim_token = #{token}")
    int markClaimPublished(@Param("eventId") String eventId, @Param("token") String token);

    @Update("UPDATE t_dispatch_event_outbox "
            + "SET status = #{status}, retry_count = #{retryCount}, last_error = #{lastError}, "
            + "next_retry_time = #{nextRetryTime}, claim_token = NULL, claimed_at = NULL, lease_until = NULL "
            + "WHERE event_id = #{eventId} AND status = 'PROCESSING' AND claim_token = #{token}")
    int markClaimFailed(@Param("eventId") String eventId,
                        @Param("token") String token,
                        @Param("status") String status,
                        @Param("retryCount") int retryCount,
                        @Param("lastError") String lastError,
                        @Param("nextRetryTime") LocalDateTime nextRetryTime);
}
