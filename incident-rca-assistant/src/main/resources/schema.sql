-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Alerts raw table (staging from Kafka)
CREATE TABLE IF NOT EXISTS alerts (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    alert_name    VARCHAR(255) NOT NULL,
    service       VARCHAR(255) NOT NULL,
    metric_name   VARCHAR(255),
    threshold     VARCHAR(100),
    current_value VARCHAR(100),
    severity      VARCHAR(50) NOT NULL,
    environment   VARCHAR(50) DEFAULT 'production',
    alert_text    TEXT NOT NULL,
    raw_payload   JSONB,
    status        VARCHAR(50) DEFAULT 'PENDING',  -- PENDING | PROCESSING | RESOLVED
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Incident embeddings for RAG (past incidents indexed as vectors)
CREATE TABLE IF NOT EXISTS incident_embeddings (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    alert_id      UUID REFERENCES alerts(id),
    alert_text    TEXT NOT NULL,
    embedding     vector(1536) NOT NULL,
    service       VARCHAR(255),
    severity      VARCHAR(50),
    rca_summary   TEXT,          -- stored after RCA is generated, used for future RAG
    feedback_score INTEGER,      -- engineer rating 1-5
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Runbooks table (knowledge base for RAG)
CREATE TABLE IF NOT EXISTS runbooks (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title         VARCHAR(500) NOT NULL,
    service       VARCHAR(255),
    content       TEXT NOT NULL,
    embedding     vector(1536),
    tags          TEXT[],
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Generated RCAs
CREATE TABLE IF NOT EXISTS rca_reports (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    alert_id          UUID REFERENCES alerts(id),
    root_cause        TEXT,
    impact            TEXT,
    timeline          TEXT,
    fix_applied       TEXT,
    prevention        TEXT,
    full_report       TEXT NOT NULL,
    model_used        VARCHAR(100),
    tokens_used       INTEGER,
    sources_used      JSONB,       -- list of runbook/incident ids used in RAG
    engineer_feedback TEXT,
    feedback_score    INTEGER,
    status            VARCHAR(50) DEFAULT 'DRAFT',  -- DRAFT | PUBLISHED | CLOSED
    slack_ts          VARCHAR(100), -- slack message timestamp for updates
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for vector similarity search
CREATE INDEX IF NOT EXISTS idx_incident_embedding
    ON incident_embeddings USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE INDEX IF NOT EXISTS idx_runbook_embedding
    ON runbooks USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- Indexes for filtering
CREATE INDEX IF NOT EXISTS idx_alerts_service ON alerts(service);
CREATE INDEX IF NOT EXISTS idx_alerts_status ON alerts(status);
CREATE INDEX IF NOT EXISTS idx_incident_service ON incident_embeddings(service);
CREATE INDEX IF NOT EXISTS idx_rca_alert ON rca_reports(alert_id);
