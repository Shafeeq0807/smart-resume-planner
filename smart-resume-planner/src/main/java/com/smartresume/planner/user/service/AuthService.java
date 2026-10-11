package com.smartresume.planner.user.service;

import com.smartresume.planner.common.config.AppProperties;
import com.smartresume.planner.common.exception.ApiException;
import com.smartresume.planner.common.util.Hashing;
import com.smartresume.planner.security.JwtService;
import com.smartresume.planner.user.domain.RefreshToken;
import com.smartresume.planner.user.domain.User;
import com.smartresume.planner.user.dto.AuthDtos.AuthResponse;
import com.smartresume.planner.user.dto.AuthDtos.LoginRequest;
import com.smartresume.planner.user.dto.AuthDtos.RegisterRequest;
import com.smartresume.planner.user.repository.RefreshTokenRepository;
import com.smartresume.planner.user.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties props;

    /** Burns the same CPU time when the email is unknown, so response timing does not reveal accounts. */
    private String dummyHash;

    @PostConstruct
    void init() {
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, ClientInfo client) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        User user = new User(email, passwordEncoder.encode(request.password()), request.fullName().trim());
        user.setLastLoginAt(Instant.now());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException race) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        log.info("Registered user id={}", user.getId());
        return issueTokens(user, UUID.randomUUID(), client);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, ClientInfo client) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email).orElse(null);
        boolean passwordOk = passwordEncoder.matches(request.password(), user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !passwordOk) {
            log.info("Failed login for email hash={}", Hashing.sha256Hex(email).substring(0, 8));
            throw ApiException.unauthorized("BAD_CREDENTIALS", "Invalid email or password");
        }
        if (!user.isEnabled()) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
                    "This account has been disabled");
        }
        user.setLastLoginAt(Instant.now());
        log.info("Login user id={}", user.getId());
        return issueTokens(user, UUID.randomUUID(), client);
    }

    /** Rotates the refresh token. Presenting an already-rotated token revokes the whole family. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String presentedToken, ClientInfo client) {
        Instant now = Instant.now();
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(presentedToken))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh token is not valid"));
        if (stored.getRevokedAt() != null) {
            int revoked = refreshTokenRepository.revokeFamily(stored.getFamilyId(), now);
            log.warn("Refresh token reuse detected for user id={}; revoked {} token(s)", stored.getUser().getId(), revoked);
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh token is not valid");
        }
        if (!stored.isActive(now)) {
            throw ApiException.unauthorized("REFRESH_TOKEN_EXPIRED", "Refresh token has expired, please sign in again");
        }
        User user = stored.getUser();
        if (!user.isEnabled()) {
            throw ApiException.unauthorized("ACCOUNT_DISABLED", "This account has been disabled");
        }
        String rawNew = newOpaqueToken();
        stored.rotateTo(hash(rawNew), now);
        refreshTokenRepository.save(new RefreshToken(user, hash(rawNew), stored.getFamilyId(),
                now.plus(props.security().jwt().refreshTokenTtl()), client.userAgent(), client.ip()));
        return AuthResponse.of(jwtService.issueAccessToken(user), rawNew, jwtService.accessTokenTtlSeconds(), user);
    }

    /** Idempotent: unknown or already-revoked tokens are ignored. */
    @Transactional
    public void logout(String presentedToken) {
        refreshTokenRepository.findByTokenHash(hash(presentedToken))
                .ifPresent(t -> refreshTokenRepository.revokeFamily(t.getFamilyId(), Instant.now()));
    }

    @Transactional
    public void revokeAllSessions(Long userId) {
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(Instant.now().minusSeconds(86_400));
        if (deleted > 0) {
            log.info("Purged {} expired refresh tokens", deleted);
        }
    }

    private AuthResponse issueTokens(User user, UUID familyId, ClientInfo client) {
        String raw = newOpaqueToken();
        refreshTokenRepository.save(new RefreshToken(user, hash(raw), familyId,
                Instant.now().plus(props.security().jwt().refreshTokenTtl()), client.userAgent(), client.ip()));
        return AuthResponse.of(jwtService.issueAccessToken(user), raw, jwtService.accessTokenTtlSeconds(), user);
    }

    private static String newOpaqueToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        return Hashing.sha256Hex(token);
    }

    /** Request metadata stored with each refresh token (audit trail for "where am I signed in"). */
    public record ClientInfo(String userAgent, String ip) {
        public static ClientInfo of(jakarta.servlet.http.HttpServletRequest request) {
            String ua = request.getHeader("User-Agent");
            return new ClientInfo(ua == null ? null : ua.substring(0, Math.min(ua.length(), 255)),
                    request.getRemoteAddr());
        }
    }
}
