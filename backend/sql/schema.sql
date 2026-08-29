CREATE TABLE image_job (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    original_size BIGINT NOT NULL,
    original_width INTEGER NOT NULL,
    original_height INTEGER NOT NULL,
    megapixels NUMERIC(10, 2) NOT NULL,
    estimated_memory_bytes BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_reason TEXT,
    original_object_key TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT image_job_status_check
        CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED', 'REJECTED'))
);

CREATE INDEX idx_image_job_client_created
    ON image_job (client_id, created_at DESC);


CREATE TABLE image_variant (
    id UUID PRIMARY KEY,
    image_id UUID NOT NULL,
    variant_type VARCHAR(20) NOT NULL,
    format VARCHAR(20) NOT NULL,
    file_size BIGINT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    object_key TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_image_variant_job
        FOREIGN KEY (image_id)
        REFERENCES image_job(id)
        ON DELETE CASCADE,

    CONSTRAINT image_variant_type_check
        CHECK (variant_type IN ('PREVIEW', 'OPTIMIZED')),

    CONSTRAINT uq_image_variant_type
        UNIQUE (image_id, variant_type)
);