CREATE TABLE idempotency_records (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    principal_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    operation           VARCHAR(100) NOT NULL,
    idempotency_key     UUID NOT NULL,
    request_hash        CHAR(64) NOT NULL,
    state               VARCHAR(20) NOT NULL DEFAULT 'in_progress',
    resource_type       VARCHAR(50),
    resource_id         UUID,
    response_status     SMALLINT,
    result_json         JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '30 days'),
    CONSTRAINT uq_idempotency_principal_operation_key
        UNIQUE (principal_id, operation, idempotency_key),
    CONSTRAINT idempotency_operation_check CHECK (
        char_length(btrim(operation)) BETWEEN 1 AND 100
    ),
    CONSTRAINT idempotency_uuid_v4_check CHECK (
        substring(idempotency_key::text FROM 15 FOR 1) = '4'
        AND substring(idempotency_key::text FROM 20 FOR 1) IN ('8', '9', 'a', 'b')
    ),
    CONSTRAINT idempotency_request_hash_check CHECK (
        request_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT idempotency_state_check CHECK (
        state IN ('in_progress', 'completed')
    ),
    CONSTRAINT idempotency_result_state_check CHECK (
        (
            state = 'in_progress'
            AND resource_type IS NULL
            AND resource_id IS NULL
            AND response_status IS NULL
            AND result_json IS NULL
        )
        OR
        (
            state = 'completed'
            AND resource_type IS NOT NULL
            AND resource_id IS NOT NULL
            AND response_status BETWEEN 200 AND 299
            AND result_json IS NOT NULL
        )
    ),
    CONSTRAINT idempotency_retention_check CHECK (
        expires_at >= created_at + INTERVAL '30 days'
    )
);

CREATE INDEX idx_idempotency_expiry
    ON idempotency_records (expires_at, id)
    WHERE state = 'completed';
CREATE INDEX idx_idempotency_principal_created
    ON idempotency_records (principal_id, created_at DESC);

CREATE FUNCTION cleanup_expired_idempotency_records(p_limit INTEGER DEFAULT 1000)
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    IF p_limit < 1 OR p_limit > 10000 THEN
        RAISE EXCEPTION 'p_limit must be between 1 and 10000';
    END IF;

    WITH expired AS (
        SELECT id
        FROM idempotency_records
        WHERE state = 'completed' AND expires_at <= NOW()
        ORDER BY expires_at, id
        LIMIT p_limit
        FOR UPDATE SKIP LOCKED
    )
    DELETE FROM idempotency_records records
    USING expired
    WHERE records.id = expired.id;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$;

COMMENT ON FUNCTION cleanup_expired_idempotency_records(INTEGER) IS
    'Schedule this bounded cleanup hook periodically; no always-running scheduler is installed by this migration.';
