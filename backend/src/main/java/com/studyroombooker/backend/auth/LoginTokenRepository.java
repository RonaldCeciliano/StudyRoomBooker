package com.studyroombooker.backend.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LoginTokenRepository {
    private final JdbcTemplate jdbc;

    public LoginTokenRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record ActiveMember(long id, String email, String name) {}

    public record ConsumedToken(String email, boolean active, Instant expiresAt) {}

    public Optional<ActiveMember> activeMember(String email) {
        return jdbc.query("SELECT id, email, name FROM member WHERE active AND lower(email) = lower(?)",
            (row, index) -> new ActiveMember(row.getLong("id"), row.getString("email"), row.getString("name")), email)
            .stream().findFirst();
    }

    /** Locks the member row so concurrent requests for the same member serialize the cooldown check. */
    public boolean issuedSince(long memberId, Instant since) {
        jdbc.query("SELECT id FROM member WHERE id = ? FOR UPDATE", (row, index) -> row.getLong(1), memberId);
        Boolean recent = jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM login_token WHERE member_id = ? AND created_at > ?)",
            Boolean.class, memberId, Timestamp.from(since));
        return Boolean.TRUE.equals(recent);
    }

    public void insert(byte[] tokenHash, long memberId, Instant createdAt, Instant expiresAt) {
        jdbc.update("INSERT INTO login_token(token_hash, member_id, created_at, expires_at) VALUES (?, ?, ?, ?)",
            tokenHash, memberId, Timestamp.from(createdAt), Timestamp.from(expiresAt));
    }

    /** Atomically deletes the token so it can be used at most once, even under concurrent submits. */
    public Optional<ConsumedToken> consume(byte[] tokenHash) {
        return jdbc.query("""
                DELETE FROM login_token t USING member m
                WHERE t.token_hash = ? AND m.id = t.member_id
                RETURNING m.email, m.active, t.expires_at
                """,
            (row, index) -> new ConsumedToken(row.getString("email"), row.getBoolean("active"),
                row.getObject("expires_at", OffsetDateTime.class).toInstant()), tokenHash)
            .stream().findFirst();
    }

    public void deleteExpired(Instant now) {
        jdbc.update("DELETE FROM login_token WHERE expires_at <= ?", Timestamp.from(now));
    }
}
