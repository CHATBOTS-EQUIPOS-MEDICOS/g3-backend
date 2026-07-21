-- Agregar columnas de feedback a la tabla chat_session para almacenar si sirvió (true/false) y comentario (text)
ALTER TABLE public.chat_session ADD COLUMN IF NOT EXISTS feedback_useful BOOLEAN;
ALTER TABLE public.chat_session ADD COLUMN IF NOT EXISTS feedback_comment TEXT;
