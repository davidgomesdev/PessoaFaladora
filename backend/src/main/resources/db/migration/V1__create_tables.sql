CREATE TABLE IF NOT EXISTS personas
(
    id        SERIAL PRIMARY KEY,
    code_name VARCHAR(50) NOT NULL UNIQUE
);

INSERT INTO personas (code_name)
SELECT candidate.code_name
FROM (VALUES ('o_fingidor'),
             ('fernando_pessoa'),
             ('alberto_caeiro'),
             ('alvaro_de_campos'),
             ('ricardo_reis'),
             ('bernardo_soares')) AS candidate(code_name)
WHERE NOT EXISTS (SELECT 1
                  FROM personas existing
                  WHERE existing.code_name = candidate.code_name);

CREATE TABLE IF NOT EXISTS sessions
(
    conversation_id UUID PRIMARY KEY,
    persona_id      INTEGER NOT NULL REFERENCES personas (id)
);

CREATE TABLE IF NOT EXISTS chat_memory
(
    id              UUID PRIMARY KEY,
    conversation_id UUID                     NOT NULL REFERENCES sessions (conversation_id) ON DELETE CASCADE,
    message_json    TEXT                     NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_chat_memory_conversation_id ON chat_memory (conversation_id);

CREATE TABLE IF NOT EXISTS chat_history
(
    id              UUID PRIMARY KEY,
    conversation_id UUID                     NOT NULL REFERENCES sessions (conversation_id) ON DELETE CASCADE,
    user_message    TEXT                     NOT NULL,
    ai_response     TEXT                     NOT NULL,
    sources_json    TEXT,
    persona_id VARCHAR
(
    50
) NOT NULL REFERENCES personas
(
    code_name
),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_chat_history_conversation_id ON chat_history (conversation_id);
