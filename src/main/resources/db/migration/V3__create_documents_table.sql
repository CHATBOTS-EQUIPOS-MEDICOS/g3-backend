CREATE TABLE IF NOT EXISTS public.documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    content_type TEXT,
    size_bytes BIGINT,
    status TEXT NOT NULL, -- 'PROCESSING', 'COMPLETED', 'FAILED', 'DELETED'
    file_data BYTEA,      -- Los bytes del documento PDF almacenados directamente
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
