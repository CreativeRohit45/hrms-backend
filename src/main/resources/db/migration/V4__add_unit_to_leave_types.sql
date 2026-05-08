ALTER TABLE leave_types ADD COLUMN unit VARCHAR(10) NOT NULL DEFAULT 'DAYS';

-- Update CMP to use HOURS unit
UPDATE leave_types SET unit = 'HOURS' WHERE code = 'CMP';
