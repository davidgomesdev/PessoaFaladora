ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS conversation_type VARCHAR (16);

ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS opponent_persona_id INTEGER REFERENCES personas (id);

UPDATE sessions
SET conversation_type = 'SINGLE'
WHERE conversation_type IS NULL;

ALTER TABLE sessions
    ALTER COLUMN conversation_type SET NOT NULL;
