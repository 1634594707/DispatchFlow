package com.fsd.common.id;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务单号（订单号 / 任务号）。
 *
 * <p>为什么不是"时间戳 + 随机数"：压测实测 {@code TSK + yyyyMMddHHmmss + random(4)} 在并发下会撞
 * {@code uk_task_no}（60 个并发写手 20 秒内撞出 3 次 DuplicateKeyException ⇒ 客户端 500、订单一起回滚）。
 * 随机数的生日碰撞是概率问题，改成**同进程内由构造保证唯一**：毫秒时间戳只负责可读性，
 * 唯一性交给单调递增的序号。
 *
 * <p>序号起点随机是为了多副本场景：两个进程在同一毫秒里都要拿到同一个序号才会撞号，
 * 而它们的序号从各自的随机起点出发、只增不减，起跑点重合之后再重合的概率实际为零。
 */
public final class BusinessNo {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private static final AtomicLong SEQUENCE = new AtomicLong(ThreadLocalRandom.current().nextLong(1L << 20));

    private BusinessNo() {
    }

    /** {@code prefix + 17 位毫秒时间戳 + 36 进制序号}，总长 ≤ 30，落在各表 varchar(64) 之内。 */
    public static String of(String prefix) {
        long seq = SEQUENCE.getAndIncrement() & 0x7fffffffL;
        return prefix + LocalDateTime.now().format(TIMESTAMP) + Long.toString(seq, 36);
    }
}
