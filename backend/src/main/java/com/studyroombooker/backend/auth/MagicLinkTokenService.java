package com.studyroombooker.backend.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and consumes magic-link tokens: 32 random bytes, stored only as a SHA-256 hash,
 * single use, and short-lived. Tokens are persisted only for active members; any other
 * email receives an unusable token that is never stored or sent, so callers cannot tell
 * the difference from the response.
 */
@Service
public class MagicLinkTokenService implements OneTimeTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final LoginTokenRepository tokens;
    private final Clock clock;
    private final Duration ttl;
    private final Duration resendCooldown;

    public MagicLinkTokenService(LoginTokenRepository tokens, Clock clock,
            @Value("${app.auth.login-token-ttl:15m}") Duration ttl,
            @Value("${app.auth.resend-cooldown:60s}") Duration resendCooldown) {
        this.tokens = tokens;
        this.clock = clock;
        this.ttl = ttl;
        this.resendCooldown = resendCooldown;
    }

    /** A generated token; {@code deliverable} is false for non-members and cooldown hits. */
    public record IssuedToken(String tokenValue, String username, Instant expiresAt, boolean deliverable)
            implements OneTimeToken {
        @Override public String getTokenValue() { return tokenValue; }
        @Override public String getUsername() { return username; }
        @Override public Instant getExpiresAt() { return expiresAt; }
        @Override public String toString() { return "IssuedToken[username=" + username + ", deliverable=" + deliverable + "]"; }
    }

    @Override
    @Transactional
    public IssuedToken generate(GenerateOneTimeTokenRequest request) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        String token = randomToken();
        String requested = request.getUsername() == null ? "" : request.getUsername().strip();
        tokens.deleteExpired(now);
        var member = tokens.activeMember(requested);
        if (member.isEmpty() || tokens.issuedSince(member.get().id(), now.minus(resendCooldown))) {
            return new IssuedToken(token, requested, expiresAt, false);
        }
        tokens.insert(hash(token), member.get().id(), now, expiresAt);
        return new IssuedToken(token, member.get().email(), expiresAt, true);
    }

    @Override
    @Transactional
    public OneTimeToken consume(OneTimeTokenAuthenticationToken authenticationToken) {
        String token = authenticationToken.getTokenValue();
        if (token == null || token.isBlank() || token.length() > 128) {
            return null;
        }
        var consumed = tokens.consume(hash(token));
        if (consumed.isEmpty() || !consumed.get().active() || !consumed.get().expiresAt().isAfter(clock.instant())) {
            return null;
        }
        return new IssuedToken(token, consumed.get().email(), consumed.get().expiresAt(), false);
    }

    public Duration ttl() { return ttl; }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
