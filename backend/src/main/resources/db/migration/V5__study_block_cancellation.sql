-- A deleted study block whose shifts must be kept (cancelled bookings awaiting LibCal
-- cancellation, or earlier cancelled shifts) is marked cancelled instead of removed.
ALTER TABLE study_block ADD COLUMN cancelled_at timestamptz;
