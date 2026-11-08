package com.studyroombooker.backend.action;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;

import com.studyroombooker.backend.service.ShiftBookingService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Issues and redeems "I booked it" links. Tokens are 32 random bytes (base64url), stored
 * hashed, bound to one member and shift, single use, and expire when the shift ends. A GET
 * preview never consumes; only {@link #markBooked} does, and a failed action rolls back so
 * the link can be retried.
 */
@Service
public class ActionTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String INVALID = "This link is invalid, expired, or already used.";

    private final JdbcTemplate jdbc;
    private final ShiftBookingService bookings;
    private final Clock clock;

    public ActionTokenService(JdbcTemplate jdbc, ShiftBookingService bookings, Clock clock) {
        this.jdbc = jdbc;
        this.bookings = bookings;
        this.clock = clock;
    }

    public record Preview(long shiftId, Instant startsAt, Instant endsAt, String plannedRoomCode,
                          String memberName, String status) {}

    /** Runs inside the caller's transaction (the reminder delivery). */
    public String issueMarkBooked(long memberId, long shiftId, Instant expiresAt) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        jdbc.update("DELETE FROM action_token WHERE expires_at <= ?", Timestamp.from(now));
        jdbc.update("""
            INSERT INTO action_token(token_hash, member_id, shift_id, action, created_at, expires_at)
            VALUES (?, ?, ?, 'MARK_BOOKED', ?, ?)
            """, hash(token), memberId, shiftId, Timestamp.from(now), Timestamp.from(expiresAt));
        return token;
    }

    public void revoke(String token) {
        jdbc.update("DELETE FROM action_token WHERE token_hash = ?", hash(token));
    }

    @Transactional(readOnly = true)
    public Preview preview(String token) {
        return jdbc.query("""
            SELECT s.id, s.starts_at, s.ends_at, r.code, m.name, s.status
            FROM action_token t
            JOIN shift s ON s.id = t.shift_id
            JOIN room r ON r.id = s.planned_room_id
            JOIN member m ON m.id = t.member_id
            WHERE t.token_hash = ? AND t.action = 'MARK_BOOKED' AND t.expires_at > ?
            """, (row, index) -> new Preview(row.getLong("id"),
                row.getObject("starts_at", OffsetDateTime.class).toInstant(),
                row.getObject("ends_at", OffsetDateTime.class).toInstant(),
                row.getString("code"), row.getString("name"), row.getString("status")),
            hashOrReject(token), Timestamp.from(clock.instant()))
            .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, INVALID));
    }

    /** Consumes the token and marks the shift booked as its member, in the planned room. */
    @Transactional
    public void markBooked(String token) {
        record Consumed(long shiftId, String email, Instant expiresAt) {}
        Optional<Consumed> consumed = jdbc.query("""
            DELETE FROM action_token t USING member m
            WHERE t.token_hash = ? AND t.action = 'MARK_BOOKED' AND m.id = t.member_id
            RETURNING t.shift_id, m.email, t.expires_at
            """, (row, index) -> new Consumed(row.getLong("shift_id"), row.getString("email"),
                row.getObject("expires_at", OffsetDateTime.class).toInstant()), hashOrReject(token))
            .stream().findFirst();
        if (consumed.isEmpty() || !consumed.get().expiresAt().isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, INVALID);
        }
        // Same rules as the dashboard: active owner, ASSIGNED or BOOKED, not ended.
        bookings.markBooked(consumed.get().shiftId(), consumed.get().email(), null);
    }

    private static byte[] hashOrReject(String token) {
        if (token == null || token.isBlank() || token.length() > 128) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, INVALID);
        }
        return hash(token);
    }

    private static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
