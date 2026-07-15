-- Add summary column to support_sessions table
ALTER TABLE public.support_sessions ADD COLUMN IF NOT EXISTS summary TEXT;
