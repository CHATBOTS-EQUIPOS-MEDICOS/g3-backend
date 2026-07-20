-- Agregar columna 'enabled' a la tabla documents
ALTER TABLE public.documents ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;
