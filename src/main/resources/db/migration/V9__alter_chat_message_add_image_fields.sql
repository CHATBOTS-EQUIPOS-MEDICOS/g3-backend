-- Alterar la tabla chat_message para soportar el almacenamiento de imágenes base64 y su tipo mime
ALTER TABLE public.chat_message
ADD COLUMN image_base64 TEXT,
ADD COLUMN image_mime_type VARCHAR(100);
