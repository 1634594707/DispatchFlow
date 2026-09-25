package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.ExternalApiKeyEntity;
import com.fsd.dispatch.mapper.ExternalApiKeyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 匿名下单这把开关的守门测试。
 *
 * <p>{@code MobileOrderAuthServiceImpl} 曾把第二道条件读成 JVM 系统属性，于是启动脚本里
 * {@code MOBILE_ORDER_REQUIRE_KEY=false} 拨不动它 —— 注释承诺"已关掉"，运行态却在返回
 * {@code MOBILE_ORDER_KEY_REQUIRED}。这类"开关只有一半有效"的缺陷不会报错，只会静默地
 * 与运维认知相反，所以把两把钥匙的组合逐个钉住。
 */
@ExtendWith(MockitoExtension.class)
class MobileOrderAuthServiceImplTest {

    @Mock
    private ExternalApiKeyMapper apiKeyMapper;

    private MobileOrderAuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new MobileOrderAuthServiceImpl(apiKeyMapper);
    }

    private void configure(boolean requireApiKey, boolean unsafeNoAuth) {
        ReflectionTestUtils.setField(authService, "requireApiKey", requireApiKey);
        ReflectionTestUtils.setField(authService, "unsafeNoAuth", unsafeNoAuth);
    }

    @Test
    void defaultConfigurationStillRequiresKey() {
        configure(true, false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> authService.validateMobileOrderKey(null));

        assertEquals("MOBILE_ORDER_KEY_REQUIRED", error.getCode());
    }

    /** 只拧一把钥匙不算关掉 —— 这一条就是当初那个"注释说是关着的、其实没关"的现场。 */
    @Test
    void requireApiKeyOffAloneDoesNotOpenTheEndpoint() {
        configure(false, false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> authService.validateMobileOrderKey("  "));

        assertEquals("MOBILE_ORDER_KEY_REQUIRED", error.getCode());
        verifyNoInteractions(apiKeyMapper);
    }

    @Test
    void unsafeNoAuthAloneDoesNotOpenTheEndpoint() {
        configure(true, true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> authService.validateMobileOrderKey(null));

        assertEquals("MOBILE_ORDER_KEY_REQUIRED", error.getCode());
    }

    @Test
    void bothSwitchesOffAllowsAnonymousOrderWithoutTouchingKeyTable() {
        configure(false, true);

        authService.validateMobileOrderKey(null);

        verifyNoInteractions(apiKeyMapper);
    }

    @Test
    void unknownKeyIsRejectedWhenAuthIsOn() {
        configure(true, false);
        when(apiKeyMapper.selectOne(any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> authService.validateMobileOrderKey("not-a-real-key"));

        assertEquals("MOBILE_ORDER_KEY_INVALID", error.getCode());
    }
}
