-- Insert TECHNICIAN role if not exists
INSERT INTO public.role (name) VALUES ('TECHNICIAN') ON CONFLICT (name) DO NOTHING;

-- Create employee_detail table
CREATE TABLE IF NOT EXISTS public.employee_detail (
    user_id UUID PRIMARY KEY REFERENCES public."user"(id) ON DELETE CASCADE,
    work_days VARCHAR(255) NOT NULL,
    work_hours VARCHAR(255) NOT NULL
);
