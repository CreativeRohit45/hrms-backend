ALTER TABLE shifts
    ADD COLUMN minimum_headcount INT NULL AFTER grace_period_minutes;
