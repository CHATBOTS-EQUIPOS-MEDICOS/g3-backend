ALTER TABLE public.password_reset_code ADD COLUMN verified BOOLEAN DEFAULT FALSE NOT NULL;
