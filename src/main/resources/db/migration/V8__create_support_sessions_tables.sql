-- Crear la tabla para las sesiones de soporte en vivo
CREATE TABLE IF NOT EXISTS public.support_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    support_id UUID,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    assigned_at TIMESTAMP WITH TIME ZONE,
    closed_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_support_sessions_user FOREIGN KEY (user_id) REFERENCES public."user"(id) ON DELETE CASCADE,
    CONSTRAINT fk_support_sessions_support FOREIGN KEY (support_id) REFERENCES public."user"(id) ON DELETE SET NULL
);

-- Crear la tabla para los mensajes de soporte en vivo
CREATE TABLE IF NOT EXISTS public.messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL,
    sender_id UUID,
    sender_type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_messages_session FOREIGN KEY (session_id) REFERENCES public.support_sessions(id) ON DELETE CASCADE
);
