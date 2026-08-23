package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.dto.ParkOrderCreateRequest;
import com.fsd.dispatch.entity.OrderIdempotencyEntity;
import com.fsd.dispatch.mapper.OrderIdempotencyMapper;
import com.fsd.dispatch.vo.ParkOrderCreateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class ParkOrderIdempotencyServiceImplTest {

    @Mock
    private OrderIdempotencyMapper idempotencyMapper;

    private ParkOrderIdempotencyServiceImpl idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new ParkOrderIdempotencyServiceImpl(idempotencyMapper, new ObjectMapper());
    }

    @Test
    void firstRequestShouldInsertProcessingRecordAndReturnNull() {
        when(idempotencyMapper.selectOne(any())).thenReturn(null);

        ParkOrderCreateResponse result = idempotencyService.tryReserve(buildRequest(), 3L);

        assertNull(result);
        ArgumentCaptor<OrderIdempotencyEntity> captor = ArgumentCaptor.forClass(OrderIdempotencyEntity.class);
        verify(idempotencyMapper).insert(captor.capture());
        assertEquals("idem-key-0001", captor.getValue().getIdempotencyKey());
        assertEquals(3L, captor.getValue().getParkId());
        assertEquals(OrderIdempotencyEntity.STATUS_PROCESSING, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getRequestHash());
    }

    @Test
    void duplicateRequestShouldReplayOriginalOrder() {
        when(idempotencyMapper.selectOne(any())).thenReturn(completedRecord());

        ParkOrderCreateResponse result = idempotencyService.tryReserve(buildRequest(), 3L);

        assertNotNull(result);
        assertEquals(5001L, result.getOrderId());
        assertEquals("ORD-5001", result.getOrderNo());
        assertTrue(result.getReplayed());
        verify(idempotencyMapper, never()).insert(any(OrderIdempotencyEntity.class));
    }

    @Test
    void duplicateRequestWithDifferentBodyShouldBeRejected() {
        when(idempotencyMapper.selectOne(any())).thenReturn(completedRecord());

        ParkOrderCreateRequest request = buildRequest();
        request.setDropoffStationId(999L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> idempotencyService.tryReserve(request, 3L));
        assertEquals("IDEMPOTENCY_KEY_MISMATCH", ex.getCode());
    }

    @Test
    void concurrentInFlightKeyShouldBeRejectedAsInProgress() {
        OrderIdempotencyEntity processing = completedRecord();
        processing.setStatus(OrderIdempotencyEntity.STATUS_PROCESSING);
        processing.setResponseSnapshot(null);
        when(idempotencyMapper.selectOne(any())).thenReturn(processing);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> idempotencyService.tryReserve(buildRequest(), 3L));
        assertEquals("IDEMPOTENCY_IN_PROGRESS", ex.getCode());
    }

    @Test
    void raceLostInsertShouldFallBackToWinnerRecordReplay() {
        when(idempotencyMapper.selectOne(any())).thenReturn(null, completedRecord());
        doThrow(new DuplicateKeyException("uk_idempotency_key")).when(idempotencyMapper).insert(any(OrderIdempotencyEntity.class));

        ParkOrderCreateResponse result = idempotencyService.tryReserve(buildRequest(), 3L);

        assertNotNull(result);
        assertTrue(result.getReplayed());
        assertEquals(5001L, result.getOrderId());
    }

    @Test
    void completeReservationShouldPersistCompletedSnapshot() {
        when(idempotencyMapper.selectOne(any())).thenReturn(completedRecord());

        ParkOrderCreateResponse response = ParkOrderCreateResponse.builder()
                .orderId(5001L)
                .orderNo("ORD-5001")
                .orderStatus("WAITING_DISPATCH")
                .taskId(7001L)
                .taskNo("TSK-7001")
                .taskStatus("ASSIGNED")
                .vehicleId(9001L)
                .message("ok")
                .replayed(false)
                .build();

        idempotencyService.completeReservation(buildRequest(), response);

        ArgumentCaptor<OrderIdempotencyEntity> captor = ArgumentCaptor.forClass(OrderIdempotencyEntity.class);
        verify(idempotencyMapper).updateById(captor.capture());
        assertEquals(OrderIdempotencyEntity.STATUS_COMPLETED, captor.getValue().getStatus());
        assertEquals(5001L, captor.getValue().getOrderId());
        assertEquals(7001L, captor.getValue().getTaskId());
        assertTrue(captor.getValue().getResponseSnapshot().contains("ORD-5001"));
    }

    @Test
    void missingOrInvalidKeyShouldBeRejected() {
        ParkOrderCreateRequest blank = buildRequest();
        blank.setIdempotencyKey(" ");
        BusinessException blankEx = assertThrows(BusinessException.class,
                () -> idempotencyService.tryReserve(blank, 3L));
        assertEquals("IDEMPOTENCY_KEY_REQUIRED", blankEx.getCode());

        ParkOrderCreateRequest invalid = buildRequest();
        invalid.setIdempotencyKey("bad key!");
        BusinessException invalidEx = assertThrows(BusinessException.class,
                () -> idempotencyService.tryReserve(invalid, 3L));
        assertEquals("IDEMPOTENCY_KEY_INVALID", invalidEx.getCode());
    }

    private ParkOrderCreateRequest buildRequest() {
        ParkOrderCreateRequest request = new ParkOrderCreateRequest();
        request.setIdempotencyKey("idem-key-0001");
        request.setExternalOrderNo("EXT-001");
        request.setParkId(3L);
        request.setPickupStationId(11L);
        request.setDropoffStationId(22L);
        request.setRouteId(5L);
        request.setPriority("P1");
        request.setRemark("备注");
        return request;
    }

    /** 与 buildRequest() 相同语义指纹的已完成记录。 */
    private OrderIdempotencyEntity completedRecord() {
        ParkOrderCreateRequest same = buildRequest();
        OrderIdempotencyEntity record = new OrderIdempotencyEntity();
        record.setId(1L);
        record.setIdempotencyKey(same.getIdempotencyKey());
        record.setRequestHash(fingerprintOf(same));
        record.setParkId(3L);
        record.setStatus(OrderIdempotencyEntity.STATUS_COMPLETED);
        record.setOrderId(5001L);
        record.setTaskId(7001L);
        record.setResponseSnapshot("{\"orderId\":5001,\"orderNo\":\"ORD-5001\",\"orderStatus\":\"WAITING_DISPATCH\","
                + "\"taskId\":7001,\"taskNo\":\"TSK-7001\",\"taskStatus\":\"ASSIGNED\",\"vehicleId\":9001,\"message\":\"ok\"}");
        return record;
    }

    private String fingerprintOf(ParkOrderCreateRequest request) {
        return ParkOrderIdempotencyServiceImpl.fingerprint(request);
    }
}
