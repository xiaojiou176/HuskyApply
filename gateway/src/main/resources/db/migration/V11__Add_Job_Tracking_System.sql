-- V11: Add Job Tracking System
-- Description: Extends jobs table and adds job_events and interviews tables for comprehensive job tracking

-- Extend jobs table with tracking-specific fields
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS job_description TEXT;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS application_deadline TIMESTAMPTZ;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS expected_salary_min INTEGER;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS expected_salary_max INTEGER;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS job_location VARCHAR(255);
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS application_method VARCHAR(100); -- 'online', 'email', 'referral', 'in_person'
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS referral_contact VARCHAR(255);
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS job_priority VARCHAR(50) DEFAULT 'MEDIUM'; -- 'HIGH', 'MEDIUM', 'LOW'
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS last_updated_at TIMESTAMPTZ DEFAULT NOW();

-- Create job_events table for timeline tracking
CREATE TABLE job_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL, -- 'APPLICATION_SUBMITTED', 'PHONE_SCREEN', 'INTERVIEW_SCHEDULED', etc.
    event_status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'COMPLETED', 'CANCELLED', 'RESCHEDULED'
    event_date TIMESTAMPTZ NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    location VARCHAR(255),
    attendees TEXT[], -- Array of contact emails/names
    duration_minutes INTEGER,
    outcome VARCHAR(100), -- 'PASSED', 'FAILED', 'WAITING_FOR_FEEDBACK', 'RESCHEDULED'
    outcome_notes TEXT,
    reminder_enabled BOOLEAN DEFAULT TRUE,
    reminder_minutes_before INTEGER DEFAULT 60, -- Default 1 hour before
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create interviews table for detailed interview tracking
CREATE TABLE interviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    job_event_id UUID REFERENCES job_events(id) ON DELETE SET NULL,
    interview_type VARCHAR(100) NOT NULL, -- 'PHONE', 'VIDEO', 'IN_PERSON', 'TECHNICAL', 'BEHAVIORAL', 'FINAL'
    interview_round INTEGER NOT NULL DEFAULT 1,
    scheduled_at TIMESTAMPTZ NOT NULL,
    duration_minutes INTEGER DEFAULT 60,
    interviewer_name VARCHAR(255),
    interviewer_title VARCHAR(255),
    interviewer_email VARCHAR(255),
    interviewer_phone VARCHAR(50),
    location VARCHAR(500),
    meeting_link VARCHAR(2048),
    preparation_notes TEXT,
    interview_questions JSONB, -- Array of questions with answers
    technical_requirements TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED', -- 'SCHEDULED', 'COMPLETED', 'CANCELLED', 'RESCHEDULED'
    feedback TEXT,
    rating INTEGER, -- 1-10 scale for self-assessment
    outcome VARCHAR(100), -- 'PASSED', 'FAILED', 'WAITING_FOR_FEEDBACK'
    follow_up_required BOOLEAN DEFAULT FALSE,
    follow_up_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create job_contacts table for networking and referral tracking
CREATE TABLE job_contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    contact_name VARCHAR(255) NOT NULL,
    contact_email VARCHAR(255),
    contact_phone VARCHAR(50),
    contact_title VARCHAR(255),
    contact_company VARCHAR(255),
    contact_department VARCHAR(255),
    relationship_type VARCHAR(100), -- 'RECRUITER', 'HIRING_MANAGER', 'REFERRAL', 'COLLEAGUE', 'HR'
    linkedin_profile VARCHAR(500),
    last_contact_date TIMESTAMPTZ,
    contact_method VARCHAR(100), -- 'EMAIL', 'PHONE', 'LINKEDIN', 'IN_PERSON'
    notes TEXT,
    is_primary_contact BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create job_documents table for tracking application materials
CREATE TABLE job_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    document_type VARCHAR(100) NOT NULL, -- 'RESUME', 'COVER_LETTER', 'PORTFOLIO', 'WRITING_SAMPLE', 'OTHER'
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(2048),
    s3_key VARCHAR(1000),
    file_size_bytes BIGINT,
    mime_type VARCHAR(100),
    version_number INTEGER DEFAULT 1,
    is_current_version BOOLEAN DEFAULT TRUE,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Add indexes for optimal query performance
CREATE INDEX idx_job_events_job_id ON job_events(job_id);
CREATE INDEX idx_job_events_event_date ON job_events(event_date);
CREATE INDEX idx_job_events_event_type ON job_events(event_type);
CREATE INDEX idx_job_events_status ON job_events(event_status);
CREATE INDEX idx_job_events_job_date ON job_events(job_id, event_date);

CREATE INDEX idx_interviews_job_id ON interviews(job_id);
CREATE INDEX idx_interviews_scheduled_at ON interviews(scheduled_at);
CREATE INDEX idx_interviews_status ON interviews(status);
CREATE INDEX idx_interviews_job_scheduled ON interviews(job_id, scheduled_at);

CREATE INDEX idx_job_contacts_job_id ON job_contacts(job_id);
CREATE INDEX idx_job_contacts_email ON job_contacts(contact_email);
CREATE INDEX idx_job_contacts_primary ON job_contacts(job_id, is_primary_contact);

CREATE INDEX idx_job_documents_job_id ON job_documents(job_id);
CREATE INDEX idx_job_documents_type ON job_documents(document_type);
CREATE INDEX idx_job_documents_current ON job_documents(job_id, is_current_version);

-- Add indexes on extended jobs table columns
CREATE INDEX idx_jobs_priority ON jobs(job_priority);
CREATE INDEX idx_jobs_deadline ON jobs(application_deadline);
CREATE INDEX idx_jobs_last_updated ON jobs(last_updated_at);
CREATE INDEX idx_jobs_user_priority ON jobs(user_id, job_priority);
CREATE INDEX idx_jobs_user_deadline ON jobs(user_id, application_deadline);

-- Create trigger to automatically update last_updated_at on jobs table
CREATE OR REPLACE FUNCTION update_job_last_updated()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_job_last_updated
    BEFORE UPDATE ON jobs
    FOR EACH ROW
    EXECUTE FUNCTION update_job_last_updated();

-- Create triggers for job_events and interviews updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_job_events_updated_at
    BEFORE UPDATE ON job_events
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_interviews_updated_at
    BEFORE UPDATE ON interviews
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_job_contacts_updated_at
    BEFORE UPDATE ON job_contacts
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Add constraints for data integrity
ALTER TABLE job_events ADD CONSTRAINT check_event_status 
    CHECK (event_status IN ('PENDING', 'COMPLETED', 'CANCELLED', 'RESCHEDULED'));

ALTER TABLE job_events ADD CONSTRAINT check_outcome 
    CHECK (outcome IS NULL OR outcome IN ('PASSED', 'FAILED', 'WAITING_FOR_FEEDBACK', 'RESCHEDULED'));

ALTER TABLE interviews ADD CONSTRAINT check_interview_status 
    CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED', 'RESCHEDULED'));

ALTER TABLE interviews ADD CONSTRAINT check_interview_outcome 
    CHECK (outcome IS NULL OR outcome IN ('PASSED', 'FAILED', 'WAITING_FOR_FEEDBACK'));

ALTER TABLE interviews ADD CONSTRAINT check_rating 
    CHECK (rating IS NULL OR (rating >= 1 AND rating <= 10));

ALTER TABLE jobs ADD CONSTRAINT check_job_priority 
    CHECK (job_priority IN ('HIGH', 'MEDIUM', 'LOW'));

ALTER TABLE jobs ADD CONSTRAINT check_application_method 
    CHECK (application_method IS NULL OR application_method IN ('online', 'email', 'referral', 'in_person'));

-- Create materialized view for job tracking dashboard
CREATE MATERIALIZED VIEW job_tracking_dashboard AS
SELECT 
    j.id as job_id,
    j.user_id,
    j.job_title,
    j.company_name,
    j.status as job_status,
    j.job_priority,
    j.application_deadline,
    j.created_at as job_created_at,
    j.last_updated_at,
    -- Event statistics
    COUNT(je.id) as total_events,
    COUNT(CASE WHEN je.event_status = 'COMPLETED' THEN 1 END) as completed_events,
    COUNT(CASE WHEN je.event_status = 'PENDING' THEN 1 END) as pending_events,
    -- Interview statistics
    COUNT(i.id) as total_interviews,
    COUNT(CASE WHEN i.status = 'COMPLETED' THEN 1 END) as completed_interviews,
    COUNT(CASE WHEN i.status = 'SCHEDULED' THEN 1 END) as scheduled_interviews,
    -- Upcoming events
    MIN(CASE WHEN je.event_status = 'PENDING' AND je.event_date > NOW() THEN je.event_date END) as next_event_date,
    MIN(CASE WHEN i.status = 'SCHEDULED' AND i.scheduled_at > NOW() THEN i.scheduled_at END) as next_interview_date,
    -- Progress indicators
    CASE 
        WHEN COUNT(CASE WHEN je.event_type = 'APPLICATION_SUBMITTED' AND je.event_status = 'COMPLETED' THEN 1 END) > 0 THEN 'APPLIED'
        ELSE 'NOT_APPLIED'
    END as application_status,
    -- Contact information
    COUNT(jc.id) as total_contacts,
    MAX(jc.last_contact_date) as last_contact_date
FROM jobs j
    LEFT JOIN job_events je ON j.id = je.job_id
    LEFT JOIN interviews i ON j.id = i.job_id
    LEFT JOIN job_contacts jc ON j.id = jc.job_id
GROUP BY j.id, j.user_id, j.job_title, j.company_name, j.status, j.job_priority, 
         j.application_deadline, j.created_at, j.last_updated_at;

-- Create index on materialized view
CREATE INDEX idx_job_tracking_dashboard_user ON job_tracking_dashboard(user_id);
CREATE INDEX idx_job_tracking_dashboard_priority ON job_tracking_dashboard(user_id, job_priority);
CREATE INDEX idx_job_tracking_dashboard_deadline ON job_tracking_dashboard(user_id, application_deadline);

-- Create function to refresh materialized view
CREATE OR REPLACE FUNCTION refresh_job_tracking_dashboard()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW job_tracking_dashboard;
END;
$$ LANGUAGE plpgsql;

-- Comments for documentation
COMMENT ON TABLE job_events IS 'Stores timeline events for job applications (interviews, phone calls, deadlines, etc.)';
COMMENT ON TABLE interviews IS 'Detailed interview tracking with preparation notes, questions, and outcomes';
COMMENT ON TABLE job_contacts IS 'Contact information for recruiters, hiring managers, and referrals';
COMMENT ON TABLE job_documents IS 'Version control for resumes, cover letters, and other application documents';
COMMENT ON MATERIALIZED VIEW job_tracking_dashboard IS 'Pre-computed view for job tracking dashboard with statistics and progress indicators';