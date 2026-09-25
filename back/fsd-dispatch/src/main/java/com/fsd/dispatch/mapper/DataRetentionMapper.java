package com.fsd.dispatch.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 无界表的保留期清理（路线图 §7.4）。
 *
 * <p>每条都按时间列做区间删除，配套索引在 V59 里补（此前四张表的时间列只出现在
 * 前导列不对的复合索引里，删起来是全表扫）。{@code LIMIT} 分批是为了别让一夜清理把
 * 一个大事务压到 undo log 上；调用方循环直到返回行数小于批次。</p>
 */
@Mapper
public interface DataRetentionMapper {

    @Delete("DELETE FROM t_fleet_telemetry_point WHERE recorded_at < #{cutoff} LIMIT #{batchSize}")
    int purgeTelemetryBefore(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);

    @Delete("DELETE FROM t_webhook_delivery_log WHERE delivered_at < #{cutoff} LIMIT #{batchSize}")
    int purgeWebhookLogBefore(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);

    /**
     * 只清 PUBLISHED 终态行：PENDING/FAILED/DEAD 的 outbox 行还是重试与排障的依据，删了就把
     * "未投递成功的事件"变成永久失踪（对应 §7.4 里 webhook 静默丢失那一条）。
     */
    @Delete("DELETE FROM t_dispatch_event_outbox WHERE status = 'PUBLISHED' AND created_at < #{cutoff} LIMIT #{batchSize}")
    int purgePublishedOutboxBefore(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);

    @Delete("DELETE FROM t_order_idempotency WHERE created_at < #{cutoff} LIMIT #{batchSize}")
    int purgeIdempotencyBefore(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
