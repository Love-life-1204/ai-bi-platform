package com.aibi.platform.identity.service;

import com.aibi.platform.common.transaction.MySqlTransactional;
import com.aibi.platform.identity.dto.RegisterRequest;
import com.aibi.platform.identity.dto.LoginData;
import com.aibi.platform.identity.dto.LoginRequest;
import com.aibi.platform.identity.dto.RefreshTokenRequest;
import com.aibi.platform.identity.dto.UserData;
import com.aibi.platform.identity.entity.UserEntity;
import com.aibi.platform.identity.mapper.UserMapper;
import com.aibi.platform.identity.security.PasswordHasher;
import com.aibi.platform.identity.security.LoginAttemptLimiter;
import com.aibi.platform.common.exception.UnauthorizedException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class IdentityService {

    private final UserMapper userMapper;
    private final PasswordHasher passwordHasher;
    private final SessionService sessionService;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final MfaService mfaService;
    private final SecureRandom secureRandom = new SecureRandom();

    public IdentityService(UserMapper userMapper, PasswordHasher passwordHasher,
            SessionService sessionService, LoginAttemptLimiter loginAttemptLimiter,
            MfaService mfaService) {
        this.userMapper = userMapper;
        this.passwordHasher = passwordHasher;
        this.sessionService = sessionService;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.mfaService = mfaService;
    }

    @MySqlTransactional(timeout = 5)
    public UserData register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        Instant now = Instant.now();
        UserEntity user = new UserEntity(
                secureRandom.nextLong(1, Long.MAX_VALUE), email, request.displayName().trim(),
                passwordHasher.hash(request.password()), "ACTIVE", null, now, now, 0);
        return toData(userMapper.insert(user));
    }

    @MySqlTransactional(timeout = 5)
    public LoginData login(LoginRequest request, String clientAddress) {
        String email = normalizeEmail(request.email());
        String attemptKey = email + "|" + clientAddress;
        Instant now = Instant.now();
        loginAttemptLimiter.checkAllowed(attemptKey, now);
        UserEntity user = userMapper.findByEmail(email).orElse(null);
        if (user == null) {
            passwordHasher.performDummyMatch(request.password());
            loginAttemptLimiter.recordFailure(attemptKey, now);
            throw new UnauthorizedException();
        }
        boolean passwordMatches = passwordHasher.matches(request.password(), user.passwordHash());
        if (!passwordMatches || !"ACTIVE".equals(user.userStatus())) {
            loginAttemptLimiter.recordFailure(attemptKey, now);
            throw new UnauthorizedException();
        }
        mfaService.requireLoginCode(user.id(), request.mfaCode());
        loginAttemptLimiter.recordSuccess(attemptKey);
        UserEntity loggedIn = new UserEntity(
                user.id(), user.email(), user.displayName(), user.passwordHash(), user.userStatus(), now,
                user.createdAt(), now, user.lockVersion() + 1);
        return sessionService.createSession(userMapper.update(loggedIn, user.lockVersion()),
                request.deviceFingerprint());
    }

    @MySqlTransactional(timeout = 5)
    public LoginData refresh(RefreshTokenRequest request) {
        return sessionService.rotate(request.refreshToken());
    }

    @MySqlTransactional(timeout = 5)
    public void logout(String authorization) {
        sessionService.logout(authorization);
    }

    public String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private UserData toData(UserEntity user) {
        return new UserData(user.id(), user.email(), user.displayName(),
                user.userStatus(), user.createdAt());
    }
}
