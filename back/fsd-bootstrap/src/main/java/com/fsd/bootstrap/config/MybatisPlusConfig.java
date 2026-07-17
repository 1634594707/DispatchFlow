package com.fsd.bootstrap.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus interceptor configuration.
 *
 * <p>SEC-17 fix: enables the {@link PaginationInnerInterceptor} so that {@code selectPage}
 * queries emit a proper {@code LIMIT ?} clause via parameter binding instead of requiring
 * callers to concatenate {@code .last("LIMIT " + n)} strings. This removes the SQL-injection
 * surface area even when the limit value is server-side bounded.
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // Allow up to 500 rows per page; callers that need more should use explicit
        // streaming. This matches the largest capped limit in the codebase (200 for
        // webhook delivery logs) with headroom.
        pagination.setMaxLimit(500L);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
