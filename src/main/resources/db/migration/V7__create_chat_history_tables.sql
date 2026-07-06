-- Crear la tabla para las sesiones de chat
CREATE TABLE IF NOT EXISTS public.chat_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    title VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_chat_session_user FOREIGN KEY (user_id) REFERENCES public."user"(id) ON DELETE CASCADE
);

-- Crear la tabla para los mensajes de chat dentro de una sesión
CREATE TABLE IF NOT EXISTS public.chat_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL,
    role VARCHAR(50) NOT NULL, -- 'USER' o 'MODEL'
    content TEXT NOT NULL,
    sources JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES public.chat_session(id) ON DELETE CASCADE
);
