CREATE TABLE IF NOT EXISTS public.role (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS public."user" (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(255),
    email VARCHAR(255),
    password VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    id_rol BIGINT NOT NULL,
    CONSTRAINT fk_user_role FOREIGN KEY (id_rol) REFERENCES public.role(id)
);

-- Insert default roles
INSERT INTO public.role (name) VALUES ('ADMIN'), ('CLIENT') ON CONFLICT (name) DO NOTHING;
