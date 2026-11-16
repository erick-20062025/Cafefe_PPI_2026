BEGIN;
ALTER TABLE orden ADD COLUMN IF NOT EXISTS cancelada_por varchar(36);
ALTER TABLE orden ADD COLUMN IF NOT EXISTS cancelada_en timestamp;
COMMIT;
