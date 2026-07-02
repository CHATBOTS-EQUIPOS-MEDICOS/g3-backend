-- Habilitar la extensión de vectores
CREATE EXTENSION IF NOT EXISTS vector;

-- Crear tabla para embeddings / fragmentos de documentos
CREATE TABLE IF NOT EXISTS public.document_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    metadata JSONB,
    embedding VECTOR(1536), -- Dimensión para embeddings de OpenAI (text-embedding-3-small)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Crear un índice HNSW para búsquedas eficientes por similitud de coseno
CREATE INDEX IF NOT EXISTS document_chunks_embedding_idx 
ON public.document_chunks USING hnsw (embedding vector_cosine_ops);
