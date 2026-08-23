package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 订单创建请求幂等记录（V50）。
 *
 * <p>同一 {@code idempotencyKey} 在数据库层由唯一键约束保证只成功创建一次订单；
 * 重复提交时返回首次成功的响应快照。</p>
 */
@Data
@TableName("t_order_idempotency")
public class OrderIdempotencyEntity {

    /** 幂等记录处理中：订单创建尚未提交完成。 */
    public static final String STATUS_PROCESSING = "PROCESSING";

    /** 幂等记录已完成：响应快照可安全重放。 */
    public static final String STATUS_COMPLETED = "COMPLETED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String idempotencyKey;

    private String requestHash;

    private Long parkId;

    private String status;

    private Long orderId;

    private Long taskId;

    private String responseSnapshot;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
