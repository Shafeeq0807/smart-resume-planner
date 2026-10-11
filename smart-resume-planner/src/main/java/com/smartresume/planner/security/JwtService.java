package com.smartresume.planner.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.smartresume.planner.common.config.AppProperties;
import com.smartresume.planner.user.domain.User;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/** Issues and validates short-lived HS256 access tokens (subject = user id, claim "role"). */
@Slf4j
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final AppProperties.Jwt props;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtService(AppProperties properties) {
        this.props = properties.security().jwt();
        SecretKey key = buildKey(props.secret());
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), JwtValidators.createDefaultWithIssuer(props.issuer()));
        nimbus.setJwtValidator(validator);
        this.decoder = nimbus;
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()))
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .claim("email", user.getEmail())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTokenTtlSeconds() {
        return props.accessTokenTtl().toSeconds();
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    /** Accepts a base64 secret (preferred) or a plain string, as long as it carries >= 256 bits. */
    private static SecretKey buildKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "app.security.jwt.secret is empty. Set JWT_SECRET (e.g. `openssl rand -base64 48`).");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException notBase64) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < MIN_SECRET_BYTES) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 bytes (256 bits). Generate one with `openssl rand -base64 48`.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
