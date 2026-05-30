CREATE TABLE IF NOT EXISTS debate_turns
(
    id
    UUID
    PRIMARY
    KEY,
    conversation_id
    UUID
    NOT
    NULL
    REFERENCES
    sessions
(
    conversation_id
) ON DELETE CASCADE,
    turn_index INTEGER NOT NULL,
    entry_type VARCHAR
(
    32
) NOT NULL,
    speaker_persona_id VARCHAR
(
    50
) REFERENCES personas
(
    code_name
),
    text TEXT NOT NULL,
    sources_json TEXT,
    created_at TIMESTAMP
  WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_debate_turns_conversation_id
    ON debate_turns (conversation_id);
