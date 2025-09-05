-- Create batch_jobs table
CREATE TABLE batch_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_jobs INTEGER DEFAULT 0,
    completed_jobs INTEGER DEFAULT 0,
    failed_jobs INTEGER DEFAULT 0,
    template_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE
);

-- Add indexes
CREATE INDEX idx_batch_jobs_user_id ON batch_jobs(user_id);
CREATE INDEX idx_batch_jobs_status ON batch_jobs(status);
CREATE INDEX idx_batch_jobs_created_at ON batch_jobs(created_at);

-- Add batch_job_id column to jobs table
ALTER TABLE jobs ADD COLUMN batch_job_id UUID REFERENCES batch_jobs(id) ON DELETE CASCADE;

-- Add index for batch_job_id
CREATE INDEX idx_jobs_batch_job_id ON jobs(batch_job_id);

-- Create trigger to update batch job statistics when jobs status changes
CREATE OR REPLACE FUNCTION update_batch_job_stats()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.batch_job_id IS NOT NULL THEN
        -- Update stats for the old batch job
        UPDATE batch_jobs SET
            completed_jobs = (SELECT COUNT(*) FROM jobs WHERE batch_job_id = OLD.batch_job_id AND status = 'COMPLETED'),
            failed_jobs = (SELECT COUNT(*) FROM jobs WHERE batch_job_id = OLD.batch_job_id AND status = 'FAILED'),
            updated_at = CURRENT_TIMESTAMP
        WHERE id = OLD.batch_job_id;
        
        -- Check if batch is complete
        UPDATE batch_jobs SET
            status = CASE 
                WHEN completed_jobs + failed_jobs = total_jobs THEN 
                    CASE WHEN failed_jobs = 0 THEN 'COMPLETED' ELSE 'FAILED' END
                ELSE 'PROCESSING' 
            END,
            completed_at = CASE 
                WHEN completed_jobs + failed_jobs = total_jobs THEN CURRENT_TIMESTAMP
                ELSE completed_at
            END
        WHERE id = OLD.batch_job_id;
    END IF;
    
    IF NEW.batch_job_id IS NOT NULL THEN
        -- Update stats for the new batch job
        UPDATE batch_jobs SET
            completed_jobs = (SELECT COUNT(*) FROM jobs WHERE batch_job_id = NEW.batch_job_id AND status = 'COMPLETED'),
            failed_jobs = (SELECT COUNT(*) FROM jobs WHERE batch_job_id = NEW.batch_job_id AND status = 'FAILED'),
            updated_at = CURRENT_TIMESTAMP
        WHERE id = NEW.batch_job_id;
        
        -- Check if batch is complete
        UPDATE batch_jobs SET
            status = CASE 
                WHEN completed_jobs + failed_jobs = total_jobs THEN 
                    CASE WHEN failed_jobs = 0 THEN 'COMPLETED' ELSE 'FAILED' END
                ELSE 'PROCESSING' 
            END,
            completed_at = CASE 
                WHEN completed_jobs + failed_jobs = total_jobs THEN CURRENT_TIMESTAMP
                ELSE completed_at
            END
        WHERE id = NEW.batch_job_id;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger on job status updates
CREATE TRIGGER trigger_update_batch_job_stats
    AFTER UPDATE OF status ON jobs
    FOR EACH ROW
    EXECUTE FUNCTION update_batch_job_stats();