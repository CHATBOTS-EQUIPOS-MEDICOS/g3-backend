-- Drop existing HNSW index to allow modifying the column
DROP INDEX IF EXISTS document_chunks_embedding_idx;

-- Alter the embedding column size to 768 dimensions for Gemini
ALTER TABLE public.document_chunks ALTER COLUMN embedding TYPE VECTOR(768);

-- Recreate the HNSW index on the updated column
CREATE INDEX IF NOT EXISTS document_chunks_embedding_idx 
ON public.document_chunks USING hnsw (embedding vector_cosine_ops);
