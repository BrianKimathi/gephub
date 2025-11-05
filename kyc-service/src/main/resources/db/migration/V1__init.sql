CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE kyc_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL,
    user_ref VARCHAR(128) NULL,
    status VARCHAR(16) NOT NULL,
    challenge_script JSONB NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_kyc_sessions_org ON kyc_sessions(organization_id);
CREATE INDEX idx_kyc_sessions_status ON kyc_sessions(status);
CREATE INDEX idx_kyc_sessions_created_at ON kyc_sessions(created_at);

CREATE TABLE kyc_media (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL REFERENCES kyc_sessions(id) ON DELETE CASCADE,
    media_type VARCHAR(16) NOT NULL,
    file_path TEXT NOT NULL,
    mime_type VARCHAR(64) NOT NULL,
    checksum VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processing_status VARCHAR(16) NOT NULL DEFAULT 'queued'
);
CREATE INDEX idx_kyc_media_session ON kyc_media(session_id);

CREATE TABLE kyc_results (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL UNIQUE REFERENCES kyc_sessions(id) ON DELETE CASCADE,
    liveness_score NUMERIC(5,4) NULL,
    reason_codes TEXT[] NULL,
    manual_review BOOLEAN NOT NULL DEFAULT FALSE,
    finalized_at TIMESTAMPTZ NULL
);

CREATE TABLE webhook_endpoints (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID NOT NULL,
    url TEXT NOT NULL,
    secret TEXT NOT NULL,
    events TEXT[] NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


