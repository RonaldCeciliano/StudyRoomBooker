-- Editing a study block cancels its BOOKED shifts (kept for LibCal follow-up) and
-- regenerates the rest. Cancelled rows must not block a regenerated shift at the same start.
ALTER TABLE shift DROP CONSTRAINT shift_block_start_unique;
CREATE UNIQUE INDEX shift_block_start_unique ON shift (study_block_id, starts_at)
    WHERE status <> 'CANCELLED';
