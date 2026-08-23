package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.dto.ParkOrderCreateRequest;
import com.fsd.dispatch.entity.OrderIdempotencyEntity;
import com.fsd.dispatch.mapper.OrderIdempotencyMapper;
import com.fsd.dispatch.service.ParkOrderIdempotencyService;
import com.fsd.dispatch.vo.ParkOrderCreateResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单创建幂等实现：数据库唯一键是唯一事实来源，跨实例安全。
 *
 * <p>占用与完成都运行在调用方的订单创建事务内：首次提交失败时整体回滚，
 * 幂等键自动释放，允许同一键重试。</p>
 */
@Slf4j
@Service
public class ParkOrderIdempotencyServiceImpl implements ParkOrderIdempotencyService {

    private static final String KEY_PATTERN = "^[A-Za-z0-9._:-]{8,128}$";

    private final OrderIdempotencyMapper idempotencyMapper;
    private final ObjectMapper objectMapper;

    public ParkOrderIdempotencyServiceImpl(OrderIdempotencyMapper idempotencyMapper, ObjectMapper objectMapper) {
        this.idempotencyMapper = idempotencyMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ParkOrderCreateResponse tryReserve(ParkOrderCreateRequest request, Long parkId) {
        String key = normalizeKey(request.getIdempotencyKey());
        String requestHash = fingerprint(request);

        OrderIdempotencyEntity existing = findByKey(key);
        if (existing != null) {
            return resolveReplay(key, existing, requestHash);
        }

        OrderIdempotencyEntity record = new OrderIdempotencyEntity();
        record.setIdempotencyKey(key);
        record.setRequestHash(requestHash);
        record.setParkId(parkId);
        record.setStatus(OrderIdempotencyEntity.STATUS_PROCESSING);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        try {
            idempotencyMapper.insert(record);
            return null;
        } catch (DuplicateKeyException race) {
            // 并发同键：另一事务已提交占用记录，按重放/冲突处理。
            OrderIdempotencyEntity winner = findByKey(key);
            if (winner == null) {
                throw new BusinessException("IDEMPOTENCY_IN_PROGRESS",
                        "相同幂等键的请求正在处理中，请稍后查询结果");
            }
            return resolveReplay(key, winner, requestHash);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeReservation(ParkOrderCreateRequest request, ParkOrderCreateResponse response) {
        String key = normalizeKey(request.getIdempotencyKey());
        OrderIdempotencyEntity record = findByKey(key);
        if (record == null) {
            log.warn("Idempotency record missing on completion, key={}", key);
            return;
        }
        record.setStatus(OrderIdempotencyEntity.STATUS_COMPLETED);
        record.setOrderId(response.getOrderId());
        record.setTaskId(response.getTaskId());
        record.setResponseSnapshot(writeSnapshot(response));
        record.setUpdatedAt(LocalDateTime.now());
        idempotencyMapper.updateById(record);
    }

    private ParkOrderCreateResponse resolveReplay(String key, OrderIdempotencyEntity record, String requestHash) {
        if (!Objects.equals(record.getRequestHash(), requestHash)) {
            throw new BusinessException("IDEMPOTENCY_KEY_MISMATCH",
                    "幂等键已被不同内容请求使用，请更换幂等键后重试");
        }
        if (OrderIdempotencyEntity.STATUS_PROCESSING.equals(record.getStatus())) {
            throw new BusinessException("IDEMPOTENCY_IN_PROGRESS",
                    "相同幂等键的请求正在处理中，请稍后查询结果");
        }
        ParkOrderCreateResponse replayed = readSnapshot(record.getResponseSnapshot());
        if (replayed == null) {
            throw new BusinessException("IDEMPOTENCY_REPLAY_FAILED",
                    "原订单结果已不可读，请联系客服核实订单状态");
        }
        replayed.setReplayed(true);
        log.info("Order idempotency replay, key={}, orderId={}", key, replayed.getOrderId());
        return replayed;
    }

    private OrderIdempotencyEntity findByKey(String key) {
        return idempotencyMapper.selectOne(new LambdaQueryWrapper<OrderIdempotencyEntity>()
                .eq(OrderIdempotencyEntity::getIdempotencyKey, key));
    }

    private String normalizeKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED",
                    "缺少幂等键：移动端下单必须携带 idempotencyKey");
        }
        String key = rawKey.trim();
        if (!key.matches(KEY_PATTERN)) {
            throw new BusinessException("IDEMPOTENCY_KEY_INVALID",
                    "幂等键格式非法：仅允许字母数字与 . _ : -，长度 8-128");
        }
        return key;
    }

    /** 请求语义指纹：只包含影响订单内容的字段，忽略传输层差异（测试同源使用）。 */
    static String fingerprint(ParkOrderCreateRequest request) {
        String canonical = String.join("\u001f",
                safe(request.getIdempotencyKey()),
                String.valueOf(request.getParkId()),
                safe(request.getExternalOrderNo() == null ? null : request.getExternalOrderNo().trim()),
                String.valueOf(request.getPickupStationId()),
                String.valueOf(request.getDropoffStationId()),
                String.valueOf(request.getRouteId()),
                safe(normalizePriority(request.getPriority())),
                safe(request.getRemark() == null ? null : request.getRemark().trim()));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String normalizePriority(String priority) {
        return priority == null || priority.isBlank() ? "P2" : priority;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private String writeSnapshot(ParkOrderCreateResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("Serialize order idempotency snapshot failed", e);
        }
    }

    private ParkOrderCreateResponse readSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(snapshot);
            return ParkOrderCreateResponse.builder()
                    .orderId(node.path("orderId").asLong(0))
                    .orderNo(node.path("orderNo").asText(null))
                    .orderStatus(node.path("orderStatus").asText(null))
                    .taskId(node.path("taskId").asLong(0))
                    .taskNo(node.path("taskNo").asText(null))
                    .taskStatus(node.path("taskStatus").asText(null))
                    .vehicleId(node.path("vehicleId").isNumber() ? node.path("vehicleId").asLong() : null)
                    .message(node.path("message").asText(null))
                    .build();
        } catch (Exception e) {
            log.warn("Deserialize order idempotency snapshot failed", e);
            return null;
        }
    }
}
