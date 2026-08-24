package com.fsd.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fsd.dispatch.entity.DispatchStrategyProfileEntity;
import com.fsd.dispatch.mapper.DispatchStrategyChangeLogMapper;
import com.fsd.dispatch.mapper.DispatchStrategyProfileMapper;
import com.fsd.dispatch.service.DispatchStrategyRuntimeService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 路线图 2.1：策略档案列表园区作用域 —— 选中园区时命中园区专属或全局模板。
 */
class DispatchStrategyParkFilterTest {

    private DispatchStrategyProfileMapper profileMapper;
    private DispatchStrategyAdminServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // LambdaQueryWrapper 需要实体的列缓存（MyBatis 启动时通常自动初始化）
        TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new MybatisConfiguration(), ""),
                DispatchStrategyProfileEntity.class);
        profileMapper = mock(DispatchStrategyProfileMapper.class);
        when(profileMapper.selectList(any())).thenReturn(List.of());
        service = new DispatchStrategyAdminServiceImpl(
                profileMapper,
                mock(DispatchStrategyChangeLogMapper.class),
                mock(DispatchStrategyRuntimeService.class));
    }

    @Test
    void scopedQueryShouldFilterByParkOrGlobalTemplate() {
        service.listProfiles(5L);

        ArgumentCaptor<Wrapper<DispatchStrategyProfileEntity>> captor =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(profileMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("park_id"), "应包含 park_id 过滤条件");
        assertTrue(sql.toLowerCase().contains("is null"), "应保留全局模板");
    }

    @Test
    void globalQueryShouldNotFilterByPark() {
        service.listProfiles();

        ArgumentCaptor<Wrapper<DispatchStrategyProfileEntity>> captor =
                ArgumentCaptor.forClass(Wrapper.class);
        verify(profileMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertFalse(sql.contains("park_id"), "全局查询不应带园区条件");
    }
}