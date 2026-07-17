package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.auth.AdminAuthContext;
import com.fsd.admin.dto.AdminChangePasswordRequest;
import com.fsd.admin.dto.AdminLoginRequest;
import com.fsd.admin.entity.AdminSessionEntity;
import com.fsd.admin.entity.AdminUserEntity;
import com.fsd.admin.mapper.AdminSessionMapper;
import com.fsd.admin.mapper.AdminUserMapper;
import com.fsd.admin.service.AdminAuthService;
import com.fsd.admin.vo.AdminLoginResponse;
import com.fsd.admin.vo.AdminUserResponse;
import com.fsd.common.enums.AdminRole;
import com.fsd.common.enums.AdminUserStatus;
import com.fsd.common.exception.BusinessException;
import com.fsd.common.security.FieldEncryptionService;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final int SESSION_TTL_HOURS = 24;
    private static final int MAX_LOGIN_ATTEMPTS_PER_MINUTE = 10;

    private final AdminUserMapper adminUserMapper;
    private final AdminSessionMapper adminSessionMapper;
    private final FieldEncryptionService fieldEncryptionService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(new DefaultCodeGenerator(HashingAlgorithm.SHA1), timeProvider);
    // TODO(B008): single-instance login rate limiter — counters live in this JVM only.
    // Multi-instance deployments must migrate to Redis so brute-force protection is shared.
    private final Map<String, LoginRateWindow> loginRateWindows = new ConcurrentHashMap<>();

    public AdminAuthServiceImpl(AdminUserMapper adminUserMapper,
                                AdminSessionMapper adminSessionMapper,
                                FieldEncryptionService fieldEncryptionService) {
        this.adminUserMapper = adminUserMapper;
        this.adminSessionMapper = adminSessionMapper;
        this.fieldEncryptionService = fieldEncryptionService;
    }

    @Override
    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request) {
        checkLoginRateLimit(request.getUsername());

        AdminUserEntity user = adminUserMapper.selectOne(new LambdaQueryWrapper<AdminUserEntity>()
                .eq(AdminUserEntity::getUsername, request.getUsername())
                .eq(AdminUserEntity::getDeleted, 0));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("ADMIN_LOGIN_FAILED", "用户名或密码错误");
        }
        if (!AdminUserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw new BusinessException("ADMIN_USER_DISABLED", "账号已被禁用");
        }
        if (isTotpEnabled(user)) {
            if (request.getTotpCode() == null || request.getTotpCode().isBlank()) {
                return AdminLoginResponse.builder()
                        .requiresTotp(true)
                        .user(toUserResponse(user))
                        .build();
            }
            if (!verifyTotp(decryptTotpSecret(user.getTotpSecret()), request.getTotpCode())) {
                throw new BusinessException("ADMIN_TOTP_INVALID", "验证码错误");
            }
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        AdminSessionEntity session = new AdminSessionEntity();
        session.setToken(token);
        session.setUserId(user.getId());
        session.setExpiresAt(LocalDateTime.now().plusHours(SESSION_TTL_HOURS));
        session.setCreatedAt(LocalDateTime.now());
        adminSessionMapper.insert(session);

        user.setLastLoginAt(LocalDateTime.now());
        adminUserMapper.updateById(user);

        return AdminLoginResponse.builder()
                .token(token)
                .requiresTotp(false)
                .user(toUserResponse(user))
                .build();
    }

    @Override
    @Transactional
    public Map<String, String> enrollTotp(Long userId) {
        AdminUserEntity user = requireActiveUser(userId);
        if (user.getRole() == null || !AdminRole.ADMIN.name().equals(user.getRole())) {
            throw new BusinessException("ADMIN_TOTP_FORBIDDEN", "仅 ADMIN 可启用 2FA");
        }
        String secret = secretGenerator.generate();
        user.setTotpSecret(fieldEncryptionService.encrypt(secret));
        user.setTotpEnabled(0);
        adminUserMapper.updateById(user);
        // B010: do not return the raw secret as a separate field (avoids plaintext logging);
        // clients scan the otpauth URL via QR code to obtain it.
        Map<String, String> result = new LinkedHashMap<>();
        result.put("otpauthUrl", "otpauth://totp/DispatchFlow:" + user.getUsername() + "?secret=" + secret + "&issuer=DispatchFlow");
        return result;
    }

    @Override
    @Transactional
    public void enableTotp(Long userId, String code) {
        AdminUserEntity user = requireActiveUser(userId);
        if (user.getTotpSecret() == null || !verifyTotp(decryptTotpSecret(user.getTotpSecret()), code)) {
            throw new BusinessException("ADMIN_TOTP_INVALID", "验证码错误");
        }
        user.setTotpEnabled(1);
        adminUserMapper.updateById(user);
    }

    @Override
    @Transactional
    public void disableTotp(Long userId, String code) {
        AdminUserEntity user = requireActiveUser(userId);
        if (isTotpEnabled(user) && !verifyTotp(decryptTotpSecret(user.getTotpSecret()), code)) {
            throw new BusinessException("ADMIN_TOTP_INVALID", "验证码错误");
        }
        user.setTotpEnabled(0);
        user.setTotpSecret(null);
        adminUserMapper.updateById(user);
    }

    @Override
    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        adminSessionMapper.delete(new LambdaQueryWrapper<AdminSessionEntity>()
                .eq(AdminSessionEntity::getToken, token));
    }

    @Override
    public AdminAuthContext resolveToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        AdminSessionEntity session = adminSessionMapper.selectOne(new LambdaQueryWrapper<AdminSessionEntity>()
                .eq(AdminSessionEntity::getToken, token));
        if (session == null) {
            return null;
        }
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            adminSessionMapper.deleteById(session.getId());
            return null;
        }
        AdminUserEntity user = adminUserMapper.selectById(session.getUserId());
        if (user == null || user.getDeleted() != null && user.getDeleted() != 0) {
            return null;
        }
        if (!AdminUserStatus.ACTIVE.name().equals(user.getStatus())) {
            return null;
        }
        return AdminAuthContext.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(resolveRole(user.getRole()))
                .token(token)
                .build();
    }

    private AdminRole resolveRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new BusinessException("ADMIN_ROLE_MISSING", "用户角色未配置");
        }
        try {
            return AdminRole.valueOf(roleName);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("ADMIN_ROLE_INVALID", "用户角色无效: " + roleName);
        }
    }

    @Override
    public AdminUserResponse getCurrentUser(Long userId) {
        AdminUserEntity user = requireActiveUser(userId);
        return toUserResponse(user);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, AdminChangePasswordRequest request) {
        AdminUserEntity user = requireActiveUser(userId);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException("ADMIN_PASSWORD_MISMATCH", "原密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        adminUserMapper.updateById(user);
        adminSessionMapper.delete(new LambdaQueryWrapper<AdminSessionEntity>()
                .eq(AdminSessionEntity::getUserId, userId));
    }

    private boolean isTotpEnabled(AdminUserEntity user) {
        return user.getTotpEnabled() != null && user.getTotpEnabled() == 1
                && user.getTotpSecret() != null && !user.getTotpSecret().isBlank();
    }

    private boolean verifyTotp(String secret, String code) {
        return codeVerifier.isValidCode(secret, code);
    }

    private String decryptTotpSecret(String stored) {
        return fieldEncryptionService.decrypt(stored);
    }

    private AdminUserEntity requireActiveUser(Long userId) {
        AdminUserEntity user = adminUserMapper.selectById(userId);
        if (user == null || user.getDeleted() != null && user.getDeleted() != 0) {
            throw new BusinessException("ADMIN_USER_NOT_FOUND", "用户不存在");
        }
        return user;
    }

    static AdminUserResponse toUserResponse(AdminUserEntity user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .status(user.getStatus())
                .totpEnabled(user.getTotpEnabled() != null && user.getTotpEnabled() == 1)
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private void checkLoginRateLimit(String username) {
        long minute = System.currentTimeMillis() / 60_000L;
        String key = "login:" + username + ":" + minute;
        // Atomic create-or-reuse via compute: avoids the computeIfAbsent + put race
        // and guarantees a consistent window across concurrent login attempts.
        LoginRateWindow window = loginRateWindows.compute(key, (k, existing) ->
                (existing != null && existing.minute == minute) ? existing : new LoginRateWindow(minute));
        if (window.count.incrementAndGet() > MAX_LOGIN_ATTEMPTS_PER_MINUTE) {
            throw new BusinessException("ADMIN_LOGIN_RATE_LIMIT", "登录尝试过于频繁，请稍后再试");
        }
        cleanupExpiredLoginWindows(minute);
    }

    private void cleanupExpiredLoginWindows(long currentMinute) {
        // Bound memory: drop windows whose minute has rolled over.
        if (loginRateWindows.size() < 128) {
            return;
        }
        loginRateWindows.entrySet().removeIf(entry -> entry.getValue().minute < currentMinute);
    }

    private static class LoginRateWindow {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger(0);

        private LoginRateWindow(long minute) {
            this.minute = minute;
        }
    }
}
