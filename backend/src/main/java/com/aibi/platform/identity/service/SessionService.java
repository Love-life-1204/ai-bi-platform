package com.aibi.platform.identity.service;

import com.aibi.platform.identity.dto.LoginData;
import com.aibi.platform.identity.dto.UserData;
import com.aibi.platform.identity.entity.SessionEntity;
import com.aibi.platform.identity.entity.UserEntity;
import com.aibi.platform.identity.mapper.SessionMapper;
import com.aibi.platform.identity.mapper.UserMapper;
import com.aibi.platform.identity.security.TokenService;
import com.aibi.platform.common.exception.UnauthorizedException;
import java.security.SecureRandom;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    private final SessionMapper sessionMapper;
    private final TokenService tokenService;
    private final UserMapper userMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public SessionService(SessionMapper sessionMapper, TokenService tokenService, UserMapper userMapper) {
        this.sessionMapper = sessionMapper;
        this.tokenService = tokenService;
        this.userMapper = userMapper;
    }

    public LoginData createSession(UserEntity user, String deviceFingerprint) {
        Instant now = Instant.now();
        String refreshToken = tokenService.newRefreshToken();
        SessionEntity session = sessionMapper.insert(new SessionEntity(
                secureRandom.nextLong(1, Long.MAX_VALUE), user.id(), tokenService.hash(refreshToken),
                tokenService.refreshExpiresAt(now), null, hashNullable(deviceFingerprint),
                null, now, 0));
        TokenService.IssuedAccessToken access = tokenService.issueAccessToken(session.id(), user.id(), now);
        return new LoginData("Bearer", access.value(), access.expiresAt(), refreshToken,
                session.expiresAt(), toUserData(user));
    }

    public LoginData rotate(String refreshToken) {
        Instant now = Instant.now();
        SessionEntity current = sessionMapper.findByRefreshTokenHash(tokenService.hash(refreshToken))
                .orElseThrow(UnauthorizedException::new);
        if (!current.isActiveAt(now) || !sessionMapper.revoke(current.id(), now)) {
            throw new UnauthorizedException();
        }
        UserEntity user = userMapper.findById(current.userId())
                .filter(value -> "ACTIVE".equals(value.userStatus()))
                .orElseThrow(UnauthorizedException::new);
        String nextRefreshToken = tokenService.newRefreshToken();
        SessionEntity next = sessionMapper.insert(new SessionEntity(
                secureRandom.nextLong(1, Long.MAX_VALUE), user.id(), tokenService.hash(nextRefreshToken),
                tokenService.refreshExpiresAt(now), null, current.deviceFingerprint(),
                current.id(), now, 0));
        TokenService.IssuedAccessToken access = tokenService.issueAccessToken(next.id(), user.id(), now);
        return new LoginData("Bearer", access.value(), access.expiresAt(), nextRefreshToken,
                next.expiresAt(), toUserData(user));
    }

    public void logout(String authorization) {
        TokenService.AccessClaims claims = authenticate(authorization);
        sessionMapper.revoke(claims.sessionId(), Instant.now());
    }

    public TokenService.AccessClaims authenticate(String authorization) {
        String token = bearerToken(authorization);
        Instant now = Instant.now();
        TokenService.AccessClaims claims = tokenService.verifyAccessToken(token, now);
        SessionEntity session = sessionMapper.findById(claims.sessionId())
                .filter(value -> value.userId() == claims.userId() && value.isActiveAt(now))
                .orElseThrow(UnauthorizedException::new);
        userMapper.findById(session.userId())
                .filter(value -> "ACTIVE".equals(value.userStatus()))
                .orElseThrow(UnauthorizedException::new);
        return claims;
    }

    public void requireActiveSession(long sessionId, long userId) {
        Instant now = Instant.now();
        sessionMapper.findById(sessionId)
                .filter(value -> value.userId() == userId && value.isActiveAt(now))
                .orElseThrow(UnauthorizedException::new);
        userMapper.findById(userId)
                .filter(value -> "ACTIVE".equals(value.userStatus()))
                .orElseThrow(UnauthorizedException::new);
    }

    public String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.length() <= "Bearer ".length()) {
            throw new UnauthorizedException();
        }
        return authorization.substring("Bearer ".length());
    }

    public String hashForIdempotency(String value) {
        return tokenService.hash(value);
    }

    private String hashNullable(String value) {
        return value == null || value.isBlank() ? null : tokenService.hash(value);
    }

    private UserData toUserData(UserEntity user) {
        return new UserData(user.id(), user.email(), user.displayName(),
                user.userStatus(), user.createdAt());
    }
}
