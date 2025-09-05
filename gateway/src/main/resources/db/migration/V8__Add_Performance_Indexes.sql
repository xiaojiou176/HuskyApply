-- V8: Add Performance-Critical Indexes for Query Optimization
-- This migration adds strategic indexes to optimize the most common query patterns

-- 1. Optimize user dashboard queries (user + status + time-based filtering)
-- This index will dramatically speed up getUserJobStats() queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_jobs_user_status_created 
    ON jobs(user_id, status, created_at DESC);

-- 2. Optimize subscription quota checks (critical path for job submissions)
-- Only index active subscriptions to keep index size minimal
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_subscriptions_active_user 
    ON user_subscriptions(user_id, status) 
    WHERE status = 'ACTIVE';

-- 3. JSONB skill search optimization on artifacts
-- This enables fast skill-based searches across generated content
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_artifacts_skills_gin 
    ON artifacts USING gin(extracted_skills);

-- 4. Template usage patterns optimization
-- Speeds up template queries with category filtering and usage sorting
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_templates_user_category_usage 
    ON templates(user_id, category, usage_count DESC) 
    WHERE usage_count IS NOT NULL;

-- 5. Batch job progress tracking optimization  
-- Enables fast lookups for batch job status monitoring
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_jobs_batch_status 
    ON jobs(batch_job_id, status) 
    WHERE batch_job_id IS NOT NULL;

-- 6. Full-text search preparation for job titles and company names
-- Add tsvector column for full-text search capability
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS search_vector tsvector;

-- Create GIN index for full-text search
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_jobs_search_vector 
    ON jobs USING gin(search_vector);

-- 7. User email lookup optimization (for authentication)
-- This should already exist but ensure it's properly indexed
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_email_lower 
    ON users(lower(email));

-- 8. Artifact content type filtering
-- Useful for filtering artifacts by type (cover_letter, resume_analysis, etc.)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_artifacts_job_content_type 
    ON artifacts(job_id, content_type);

-- 9. Job creation time clustering for archival queries
-- Helps with time-based queries and data archival processes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_jobs_created_at_status 
    ON jobs(created_at DESC, status) 
    WHERE status IN ('COMPLETED', 'FAILED');

-- 10. User subscription expiration tracking
-- Critical for subscription management and renewal notifications
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_subscriptions_expiry 
    ON user_subscriptions(expires_at) 
    WHERE status = 'ACTIVE' AND expires_at IS NOT NULL;

-- Create function to automatically update search vector
CREATE OR REPLACE FUNCTION update_job_search_vector() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('english', 
        COALESCE(NEW.job_title, '') || ' ' || 
        COALESCE(NEW.company_name, '') || ' ' ||
        COALESCE(NEW.job_description_summary, '')
    );
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

-- Create trigger to update search vector on job changes
DROP TRIGGER IF EXISTS update_job_search_trigger ON jobs;
CREATE TRIGGER update_job_search_trigger
    BEFORE INSERT OR UPDATE ON jobs
    FOR EACH ROW EXECUTE FUNCTION update_job_search_vector();

-- Update existing rows with search vectors
UPDATE jobs SET search_vector = to_tsvector('english', 
    COALESCE(job_title, '') || ' ' || 
    COALESCE(company_name, '') || ' ' ||
    COALESCE(job_description_summary, '')
) WHERE search_vector IS NULL;

-- Add table statistics refresh to help query planner
ANALYZE jobs;
ANALYZE artifacts; 
ANALYZE users;
ANALYZE user_subscriptions;
ANALYZE templates;