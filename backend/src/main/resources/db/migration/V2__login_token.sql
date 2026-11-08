-- Magic-link sign-in tokens. Only the SHA-256 hash of each token is stored; the raw
-- token exists only in the emailed link. Rows are deleted when consumed or expired.
CREATE TABLE login_token (
    token_hash bytea PRIMARY KEY CHECK (octet_length(token_hash) = 32),
    member_id bigint NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    CHECK (expires_at > created_at)
);
CREATE INDEX login_token_member_created ON login_token (member_id, created_at);
CREATE INDEX login_token_expires_at ON login_token (expires_at);
