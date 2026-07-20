-- Agregar columnas de inactividad a chat_session y support_sessions
ALTER TABLE public.chat_session ADD COLUMN IF NOT EXISTS prompt_sent BOOLEAN DEFAULT FALSE;
ALTER TABLE public.chat_session ADD COLUMN IF NOT EXISTS last_user_activity TIMESTAMP WITH TIME ZONE;
UPDATE public.chat_session SET last_user_activity = created_at WHERE last_user_activity IS NULL;

ALTER TABLE public.support_sessions ADD COLUMN IF NOT EXISTS prompt_sent BOOLEAN DEFAULT FALSE;
ALTER TABLE public.support_sessions ADD COLUMN IF NOT EXISTS last_user_activity TIMESTAMP WITH TIME ZONE;
UPDATE public.support_sessions SET last_user_activity = created_at WHERE last_user_activity IS NULL;
