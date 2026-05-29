DROP TABLE IF EXISTS chat_history;

CREATE TABLE chat_history
(
    id              UUID PRIMARY KEY,
    conversation_id UUID                     NOT NULL REFERENCES sessions (conversation_id) ON DELETE CASCADE,
    user_message    TEXT                     NOT NULL,
    ai_response     TEXT                     NOT NULL,
    sources_json    TEXT,
    persona_code    VARCHAR(50),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_chat_history_conversation_id ON chat_history (conversation_id);
