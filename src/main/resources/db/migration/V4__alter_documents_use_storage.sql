-- Eliminar la columna pesada de bytes
ALTER TABLE public.documents DROP COLUMN IF EXISTS file_data;

-- Agregar columna para almacenar la ruta en el bucket
ALTER TABLE public.documents ADD COLUMN IF NOT EXISTS storage_path TEXT;

-- Crear automáticamente el bucket 'documents' en Supabase Storage si no existe
INSERT INTO storage.buckets (id, name, public) 
VALUES ('documents', 'documents', true) 
ON CONFLICT (id) DO NOTHING;
