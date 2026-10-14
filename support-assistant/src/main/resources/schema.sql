-- Production Support Assistant schema. PostgreSQL 16 + pgvector.
--
-- This file OWNS the schema. spring.jpa.hibernate.ddl-auto is `none` and
-- spring.sql.init.mode is `always`, so Hibernate only validates against what
-- these statements build.
--
-- PostgreSQL rather than MySQL because this project needs pgvector for
-- similarity search AND ts_rank_cd for full-text ranking, and it needs them in
-- the same query so a clearance filter applies to both.
--
-- The image must be pgvector/pgvector. CREATE EXTENSION vector will not find the
-- extension on a stock postgres image, and the failure is at start-up.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS number_sequence (
    sequence_name VARCHAR(40) NOT NULL PRIMARY KEY,
    next_value    BIGINT      NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS roles (
    role_id     BIGSERIAL PRIMARY KEY,
    -- No ROLE_ prefix. hasRole() adds it before comparing, so storing it here
    -- gives ROLE_ROLE_LEAD and every check fails as though the role had never
    -- been granted.
    name        VARCHAR(40)  NOT NULL UNIQUE,
    description VARCHAR(200),
    -- The highest document classification this role may retrieve. Carried on the
    -- role rather than checked ad hoc, because retrieval is the least obvious
    -- place a restricted runbook leaks.
    max_classification VARCHAR(20) NOT NULL DEFAULT 'INTERNAL',
    CONSTRAINT ck_role_classification
        CHECK (max_classification IN ('PUBLIC','INTERNAL','RESTRICTED'))
);

CREATE TABLE IF NOT EXISTS users (
    user_id         BIGSERIAL PRIMARY KEY,
    username        VARCHAR(80)  NOT NULL UNIQUE,
    email           VARCHAR(150) NOT NULL UNIQUE,
    display_name    VARCHAR(150) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    team            VARCHAR(80),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts INT          NOT NULL DEFAULT 0,
    locked_until    TIMESTAMPTZ,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    -- RESTRICT on the role side: removing a role must not silently strip a
    -- clearance from everyone holding it.
    role_id BIGINT NOT NULL REFERENCES roles (role_id) ON DELETE RESTRICT,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS incidents (
    incident_id        BIGSERIAL PRIMARY KEY,
    incident_number    VARCHAR(20)  NOT NULL UNIQUE,
    title              VARCHAR(300) NOT NULL,
    description        TEXT         NOT NULL,
    -- An assistant-written summary. Stored beside the original, never replacing
    -- it, and read by nothing else: an RCA built on a summary is an analysis of
    -- an analysis.
    ai_summary         TEXT,
    severity           VARCHAR(10)  NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    service            VARCHAR(100) NOT NULL,
    customer_impacting BOOLEAN      NOT NULL DEFAULT FALSE,
    detected_at        TIMESTAMPTZ  NOT NULL,
    acknowledged_at    TIMESTAMPTZ,
    -- Separate from resolved_at, deliberately. Customer impact stops when the
    -- bleeding stops; the fault is often repaired days later. Collapsing them
    -- makes time-to-mitigate unmeasurable and overstates impact on everything.
    mitigated_at       TIMESTAMPTZ,
    resolved_at        TIMESTAMPTZ,
    -- Written by a person. Nothing in the assistant writes here; the RCA feature
    -- produces a draft and a human decides what goes in this column.
    root_cause         TEXT,
    resolution_summary TEXT,
    reported_by        VARCHAR(80),
    assigned_to        VARCHAR(80),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_incident_severity CHECK (severity IN ('SEV1','SEV2','SEV3','SEV4')),
    CONSTRAINT ck_incident_status CHECK (status IN
        ('NEW','ACKNOWLEDGED','INVESTIGATING','MITIGATED','RESOLVED','CLOSED'))
);
CREATE INDEX IF NOT EXISTS ix_incident_status ON incidents (status);
CREATE INDEX IF NOT EXISTS ix_incident_service ON incidents (service);
CREATE INDEX IF NOT EXISTS ix_incident_detected ON incidents (detected_at);
-- Supports the precedent search, which ranks past incidents by full-text
-- similarity to a new one.
CREATE INDEX IF NOT EXISTS ix_incident_fts ON incidents
    USING GIN (to_tsvector('english', title || ' ' || description));

-- Append only. Correcting the record means adding an entry, not editing one: the
-- timeline is the evidence every generated analysis is grounded against, and
-- evidence that can be rewritten after the fact is not evidence.
CREATE TABLE IF NOT EXISTS timeline_entries (
    entry_id    BIGSERIAL PRIMARY KEY,
    incident_id BIGINT      NOT NULL REFERENCES incidents (incident_id) ON DELETE RESTRICT,
    -- When it happened, which is not when it was recorded. Engineers write up the
    -- last twenty minutes once the fire is out; ordering by insertion produces a
    -- sequence of events that never occurred, and the RCA reasons from it.
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    entry_type  VARCHAR(20) NOT NULL,
    -- ASSISTANT entries are excluded from RCA evidence. An analysis treating its
    -- own prior output as an observation compounds its errors until nothing
    -- traces back to what happened.
    source      VARCHAR(20) NOT NULL DEFAULT 'HUMAN',
    content     TEXT        NOT NULL,
    author      VARCHAR(80),
    CONSTRAINT ck_entry_type CHECK (entry_type IN
        ('OBSERVATION','ACTION','DECISION','STATUS_CHANGE','ANALYSIS')),
    CONSTRAINT ck_entry_source CHECK (source IN ('HUMAN','SYSTEM','ASSISTANT'))
);
CREATE INDEX IF NOT EXISTS ix_timeline_incident
    ON timeline_entries (incident_id, occurred_at);
CREATE INDEX IF NOT EXISTS ix_timeline_source ON timeline_entries (source);

CREATE TABLE IF NOT EXISTS kb_documents (
    document_id      BIGSERIAL PRIMARY KEY,
    -- Stable across versions. Two versions of one runbook share a slug.
    slug             VARCHAR(200) NOT NULL,
    title            VARCHAR(300) NOT NULL,
    version_number   INT          NOT NULL DEFAULT 1,
    current_version  BOOLEAN      NOT NULL DEFAULT TRUE,
    supersedes_id    BIGINT,
    classification   VARCHAR(20)  NOT NULL DEFAULT 'INTERNAL',
    service          VARCHAR(100),
    source_filename  VARCHAR(300),
    content_hash     VARCHAR(64),
    chunk_count      INT          NOT NULL DEFAULT 0,
    -- Recorded per document. Vectors from different embedding models are not
    -- comparable: cosine distance between two different spaces is a number
    -- without meaning. A model swap without re-indexing does not fail, it
    -- silently returns worse results, so this column is how it gets found.
    embedding_model     VARCHAR(100),
    embedding_dimension INT,
    uploaded_by      VARCHAR(80),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ux_document_slug_version UNIQUE (slug, version_number),
    CONSTRAINT ck_document_classification
        CHECK (classification IN ('PUBLIC','INTERNAL','RESTRICTED'))
);
CREATE INDEX IF NOT EXISTS ix_document_slug ON kb_documents (slug);
CREATE INDEX IF NOT EXISTS ix_document_current ON kb_documents (current_version);
CREATE INDEX IF NOT EXISTS ix_document_service ON kb_documents (service);

CREATE TABLE IF NOT EXISTS document_chunks (
    chunk_id       BIGSERIAL PRIMARY KEY,
    document_id    BIGINT NOT NULL REFERENCES kb_documents (document_id) ON DELETE CASCADE,
    ordinal        INT    NOT NULL,
    -- The heading path. Embedded WITH the content: "1. Confirm the primary is
    -- healthy" embeds almost identically whichever runbook it came from.
    breadcrumb     VARCHAR(500),
    content        TEXT   NOT NULL,
    char_count     INT    NOT NULL DEFAULT 0,
    step_count     INT    NOT NULL DEFAULT 0,
    -- False when the numbered steps here may not be the whole procedure. A chunk
    -- holding steps 4 to 7 of a nine-step recovery answers the question asked and
    -- omits the part that makes it safe, so answers citing it carry a warning.
    complete_procedure BOOLEAN NOT NULL DEFAULT TRUE,
    -- Error codes, exception names, pool identifiers. Extracted at ingestion
    -- rather than per query, because the literal check runs on every search.
    literal_tokens TEXT,
    embedded_text  TEXT,
    -- 768 matches nomic-embed-text. text-embedding-3-small is 1536. A mismatch
    -- fails at insert, which is the good outcome -- silent degradation is not.
    embedding      vector(768),
    -- Generated, so it cannot drift from the content it indexes.
    content_tsv    tsvector GENERATED ALWAYS AS (to_tsvector('english', content)) STORED
);
CREATE INDEX IF NOT EXISTS ix_chunk_document ON document_chunks (document_id, ordinal);
CREATE INDEX IF NOT EXISTS ix_chunk_procedure ON document_chunks (complete_procedure);
CREATE INDEX IF NOT EXISTS ix_chunk_tsv ON document_chunks USING GIN (content_tsv);
-- HNSW rather than IVFFlat: it needs no training pass, so it works on an empty
-- table and stays correct as documents are added one at a time. vector_cosine_ops
-- must match the <=> operator used in the query, or the index is silently unused.
CREATE INDEX IF NOT EXISTS ix_chunk_embedding ON document_chunks
    USING hnsw (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS audit_events (
    audit_id            BIGSERIAL PRIMARY KEY,
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor               VARCHAR(80) NOT NULL,
    action              VARCHAR(60) NOT NULL,
    entity_type         VARCHAR(40),
    entity_id           VARCHAR(40),
    -- SHA-256 of the prompt, never the prompt. Incident text contains customer
    -- data and a long-retention audit table is the wrong place for a second copy.
    prompt_hash         VARCHAR(64),
    model_name          VARCHAR(100),
    -- What the model was shown, and what verification concluded. Without these
    -- you cannot tell weeks later whether a wrong answer came from bad retrieval
    -- or bad generation, and those have completely different fixes.
    retrieved_chunk_ids VARCHAR(500),
    evidence_refs       VARCHAR(300),
    verification_result VARCHAR(20),
    correction_count    INT,
    duration_ms         BIGINT,
    detail              VARCHAR(1000)
);
CREATE INDEX IF NOT EXISTS ix_audit_at ON audit_events (occurred_at);
CREATE INDEX IF NOT EXISTS ix_audit_actor ON audit_events (actor);
CREATE INDEX IF NOT EXISTS ix_audit_action ON audit_events (action);
CREATE INDEX IF NOT EXISTS ix_audit_entity ON audit_events (entity_type, entity_id);
