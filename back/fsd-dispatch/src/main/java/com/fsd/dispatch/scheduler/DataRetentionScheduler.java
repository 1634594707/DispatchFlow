package com.fsd.dispatch.scheduler;

import com.fsd.dispatch.mapper.DataRetentionMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 无界表保留期清理（路线图 §7.4）。
 *
 * <p>这四张表只有写入没有生命周期：遥测点、webhook 投递日志、outbox 已发布行、下单幂等键。
 * 本地库实测 outbox 已 2270 行 / 1.52 MB 且在单调增长，线上只会更快。按时间列分批删，
 * 时间列的索引由 V59 补齐（此前四张表的时间列只在"前导列不对"的复合索引里，删一次全表扫一次）。</p>
 *
 * <p>两条刻意的保守设定：① outbox 只删 {@code status='PUBLISHED'} 终态行，PENDING/FAILED/DEAD
 * 留着当重试与排障依据；② 保留期默认给得很宽（30/90 天），因为幂等窗口和 webhook 审计窗口的
 * 业务下限本轮没有依据可定 —— 收短之前必须先确认业务去重窗口，否则清理本身会制造重复下单。</p>
 */
@Component
public class DataRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionScheduler.class);

    private final List<PurgeTarget> targets;
    private final boolean enabled;
    private final int batchSize;
    private final int maxBatchesPerRun;

    public DataRetentionScheduler(DataRetentionMapper mapper,
                                  @Value("${fsd.data-retention.enabled:true}") boolean enabled,
                                  @Value("${fsd.data-retention.batch-size:500}") int batchSize,
                                  @Value("${fsd.data-retention.max-batches-per-run:200}") int maxBatchesPerRun,
                                  @Value("${fsd.data-retention.telemetry-days:30}") long telemetryDays,
                                  @Value("${fsd.data-retention.webhook-log-days:30}") long webhookLogDays,
                                  @Value("${fsd.data-retention.outbox-published-days:30}") long outboxPublishedDays,
                                  @Value("${fsd.data-retention.idempotency-days:90}") long idempotencyDays) {
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.maxBatchesPerRun = maxBatchesPerRun;
        this.targets = List.of(
                new PurgeTarget("t_fleet_telemetry_point", telemetryDays, mapper::purgeTelemetryBefore),
                new PurgeTarget("t_webhook_delivery_log", webhookLogDays, mapper::purgeWebhookLogBefore),
                new PurgeTarget("t_dispatch_event_outbox(PUBLISHED)", outboxPublishedDays, mapper::purgePublishedOutboxBefore),
                new PurgeTarget("t_order_idempotency", idempotencyDays, mapper::purgeIdempotencyBefore));
    }

    @Scheduled(cron = "${fsd.data-retention.cron:0 30 3 * * *}")
    public void purgeExpired() {
        if (!enabled) {
            log.debug("Data retention purge disabled (fsd.data-retention.enabled=false)");
            return;
        }
        for (PurgeTarget target : targets) {
            purge(target);
        }
    }

    /** 删到"这一批没填满"就停；{@code maxBatchesPerRun} 是护栏，别让一次清理独占整夜。 */
    private void purge(PurgeTarget target) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(target.retentionDays());
        int deletedTotal = 0;
        int batches = 0;
        while (batches < maxBatchesPerRun) {
            int deleted = target.purger().delete(cutoff, batchSize);
            deletedTotal += deleted;
            batches++;
            if (deleted < batchSize) {
                break;
            }
        }
        if (deletedTotal > 0) {
            log.info("Retention purge table={} retentionDays={} deleted={} batches={} cutoff={}",
                    target.table(), target.retentionDays(), deletedTotal, batches, cutoff);
        }
        if (batches >= maxBatchesPerRun) {
            log.warn("Retention purge table={} hit max-batches-per-run={} with rows possibly left",
                    target.table(), maxBatchesPerRun);
        }
    }

    @FunctionalInterface
    interface Purge {
        int delete(LocalDateTime cutoff, int batchSize);
    }

    private record PurgeTarget(String table, long retentionDays, Purge purger) {
    }
}
