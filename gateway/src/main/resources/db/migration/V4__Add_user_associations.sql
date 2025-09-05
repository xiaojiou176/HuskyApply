-- V4: Add user associations to jobs and create templates table

-- Add user_id to jobs table for user association
ALTER TABLE jobs 
ADD COLUMN user_id UUID,
ADD COLUMN job_title VARCHAR(255),
ADD COLUMN company_name VARCHAR(255),
ADD CONSTRAINT fk_jobs_user
    FOREIGN KEY (user_id) 
    REFERENCES users(id) 
    ON DELETE CASCADE;

-- Create index for user queries
CREATE INDEX idx_jobs_user_id ON jobs(user_id);
CREATE INDEX idx_jobs_user_created ON jobs(user_id, created_at DESC);

-- Create templates table for reusable cover letter templates
CREATE TABLE templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    content TEXT NOT NULL,
    category VARCHAR(100),
    is_default BOOLEAN DEFAULT FALSE,
    usage_count INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_templates_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

-- Create indexes for templates
CREATE INDEX idx_templates_user_id ON templates(user_id);
CREATE INDEX idx_templates_category ON templates(category);
CREATE INDEX idx_templates_default ON templates(user_id, is_default);

-- Add some default template categories
INSERT INTO templates (user_id, name, description, content, category, is_default) 
SELECT u.id, 'Professional Template', 'Standard professional cover letter template', 
       'Dear Hiring Manager,\n\nI am writing to express my strong interest in the {job_title} position at {company_name}. With my background in {relevant_skills}, I am confident that I would be a valuable addition to your team.\n\n{experience_paragraph}\n\n{skills_paragraph}\n\nThank you for considering my application. I look forward to the opportunity to discuss how my skills and experience can contribute to {company_name}.\n\nSincerely,\n{user_name}',
       'professional', true
FROM users u
WHERE NOT EXISTS (SELECT 1 FROM templates t WHERE t.user_id = u.id AND t.is_default = true);

-- Add job statistics view
CREATE OR REPLACE VIEW user_job_stats AS
SELECT 
    j.user_id,
    COUNT(*) as total_jobs,
    COUNT(CASE WHEN j.status = 'COMPLETED' THEN 1 END) as completed_jobs,
    COUNT(CASE WHEN j.status = 'FAILED' THEN 1 END) as failed_jobs,
    COUNT(CASE WHEN j.status = 'PENDING' THEN 1 END) as pending_jobs,
    COUNT(CASE WHEN j.status = 'PROCESSING' THEN 1 END) as processing_jobs,
    MAX(j.created_at) as last_job_date,
    COUNT(CASE WHEN j.created_at > NOW() - INTERVAL '7 days' THEN 1 END) as jobs_this_week,
    COUNT(CASE WHEN j.created_at > NOW() - INTERVAL '30 days' THEN 1 END) as jobs_this_month
FROM jobs j
GROUP BY j.user_id;