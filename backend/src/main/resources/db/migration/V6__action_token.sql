-- Single-use email action links (ARCHITECTURE.md "Action Tokens"). Only the SHA-256 hash
-- is stored; each token is tied to one member, one shift, and one action.
CREATE TABLE action_token (
    token_hash bytea PRIMARY KEY CHECK (octet_length(token_hash) = 32),
    member_id bigint NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    shift_id bigint NOT NULL REFERENCES shift(id) ON DELETE CASCADE,
    action text NOT NULL CHECK (action IN ('MARK_BOOKED')),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    CHECK (expires_at > created_at)
);
CREATE INDEX action_token_expires_at ON action_token (expires_at);
