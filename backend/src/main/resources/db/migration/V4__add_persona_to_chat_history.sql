ALTER TABLE chat_history
    ADD COLUMN IF NOT EXISTS persona_code VARCHAR(50);
