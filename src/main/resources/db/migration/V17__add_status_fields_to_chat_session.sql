-- Agregar campos para el estado de la sesión de chat (si está cerrada y cuándo se cerró)
ALTER TABLE public.chat_session ADD COLUMN IF NOT EXISTS is_closed BOOLEAN DEFAULT FALSE;
ALTER TABLE public.chat_session ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP WITH TIME ZONE;
