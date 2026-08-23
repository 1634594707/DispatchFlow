package com.fsd.bootstrap.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fsd.common.exception.BusinessException;
import com.fsd.common.model.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 路线图 Phase 4：敏感字段不得出现在错误响应中。
 *
 * <p>未捕获异常统一返回固定文案（INTERNAL_ERROR），异常细节只进服务端日志；
 * BusinessException 仅透出业务码与业务消息，不携带堆栈或类名。</p>
 */
class GlobalExceptionHandlerSanitizationTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unhandledExceptionShouldNotLeakMessageOrClass() {
        Exception internal = new IllegalStateException(
                "jdbc:mysql://db:3306/fsd_core?user=admin&password=secret-token-value");

        ResponseEntity<ApiResponse<Void>> response = handler.handleException(internal);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        String body = String.valueOf(response.getBody());
        assertFalse(body.contains("secret-token-value"), "响应体不得包含异常内部信息");
        assertFalse(body.contains("IllegalStateException"), "响应体不得包含异常类名");
        assertFalse(body.contains("jdbc"), "响应体不得包含连接串");
        assertFalse(body.contains("password"), "响应体不得包含凭据字段");
    }

    @Test
    void businessExceptionShouldExposeCodeAndBusinessMessageOnly() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(
                new BusinessException("ADMIN_AUTH_FAILED", "登录已失效，请重新登录"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("ADMIN_AUTH_FAILED", response.getBody().getCode());
        assertFalse(String.valueOf(response.getBody()).contains("token"));
    }


}