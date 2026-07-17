-- Agregar el campo liked a la tabla chat_message para indicar like (true), dislike (false) o sin calificar (null)
ALTER TABLE public.chat_message ADD COLUMN IF NOT EXISTS liked BOOLEAN;
