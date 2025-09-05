-- V9: Add Materialized View for Dashboard Statistics
-- This creates a pre-aggregated view to dramatically speed up dashboard queries

-- Create materialized view for user job statistics
CREATE MATERIALIZED VIEW IF NOT EXISTS user_job_stats_mv AS
SELECT 
    user_id,
    COUNT(*) as total_jobs,
    COUNT(*) FILTER (WHERE status = 'COMPLETED') as completed_jobs,
    COUNT(*) FILTER (WHERE status = 'FAILED') as failed_jobs,
    COUNT(*) FILTER (WHERE status = 'PENDING') as pending_jobs,
    COUNT(*) FILTER (WHERE status = 'PROCESSING') as processing_jobs,
    MAX(created_at) as last_job_date,
    COUNT(*) FILTER (WHERE created_at > NOW() - INTERVAL '7 days') as jobs_this_week,
    COUNT(*) FILTER (WHERE created_at > NOW() - INTERVAL '30 days') as jobs_this_month,
    COUNT(*) FILTER (WHERE created_at > NOW() - INTERVAL '90 days') as jobs_this_quarter,
    AVG(CASE WHEN status = 'COMPLETED' AND created_at > NOW() - INTERVAL '30 days' 
        THEN EXTRACT(EPOCH FROM (updated_at - created_at)) END) as avg_processing_time_seconds,
    COUNT(DISTINCT CASE WHEN status = 'COMPLETED' 
        THEN company_name END) as unique_companies_applied
FROM jobs 
GROUP BY user_id;

-- Create unique index on the materialized view for fast lookups
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_job_stats_mv_user_id 
    ON user_job_stats_mv(user_id);

-- Create additional indexes for common dashboard queries
CREATE INDEX IF NOT EXISTS idx_user_job_stats_mv_last_activity 
    ON user_job_stats_mv(last_job_date DESC);

-- Create function to refresh materialized view
CREATE OR REPLACE FUNCTION refresh_user_job_stats() RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY user_job_stats_mv;
    -- Update last refresh timestamp
    INSERT INTO system_maintenance_log (operation, completed_at, details)
    VALUES ('refresh_user_job_stats', NOW(), 'Materialized view refreshed successfully')
    ON CONFLICT (operation) DO UPDATE SET 
        completed_at = NOW(),
        details = 'Materialized view refreshed successfully';
EXCEPTION
    WHEN OTHERS THEN
        -- Log error but don't fail
        INSERT INTO system_maintenance_log (operation, completed_at, details)
        VALUES ('refresh_user_job_stats', NOW(), 'ERROR: ' || SQLERRM)
        ON CONFLICT (operation) DO UPDATE SET 
            completed_at = NOW(),
            details = 'ERROR: ' || SQLERRM;
END;
$$ LANGUAGE plpgsql;

-- Create system maintenance log table if it doesn't exist
CREATE TABLE IF NOT EXISTS system_maintenance_log (
    operation VARCHAR(100) PRIMARY KEY,
    completed_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    details TEXT
);

-- Create a job performance metrics view for advanced analytics
CREATE MATERIALIZED VIEW IF NOT EXISTS job_performance_metrics_mv AS
SELECT 
    DATE_TRUNC('day', created_at) as date,
    status,
    COUNT(*) as job_count,
    AVG(EXTRACT(EPOCH FROM (updated_at - created_at))) as avg_processing_time_seconds,
    PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (updated_at - created_at))) as median_processing_time_seconds,
    PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (updated_at - created_at))) as p95_processing_time_seconds,
    COUNT(DISTINCT user_id) as unique_users,
    COUNT(*) FILTER (WHERE created_at >= NOW() - INTERVAL '1 hour') as recent_jobs
FROM jobs 
WHERE created_at >= NOW() - INTERVAL '90 days'  -- Keep last 90 days
GROUP BY DATE_TRUNC('day', created_at), status;

-- Index for job performance metrics
CREATE INDEX IF NOT EXISTS idx_job_performance_metrics_mv_date_status 
    ON job_performance_metrics_mv(date DESC, status);

-- Function to refresh performance metrics
CREATE OR REPLACE FUNCTION refresh_job_performance_metrics() RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY job_performance_metrics_mv;
END;
$$ LANGUAGE plpgsql;

-- Create a popular skills aggregation view
CREATE MATERIALIZED VIEW IF NOT EXISTS popular_skills_mv AS
SELECT 
    skill,
    COUNT(*) as frequency,
    COUNT(DISTINCT a.job_id) as job_count,
    COUNT(DISTINCT j.user_id) as user_count,
    AVG(CASE WHEN j.status = 'COMPLETED' THEN 1 ELSE 0 END) as success_rate
FROM artifacts a
JOIN jobs j ON a.job_id = j.id
CROSS JOIN LATERAL (
    SELECT jsonb_array_elements_text(a.extracted_skills->'skills') as skill
    WHERE a.extracted_skills ? 'skills'
) skills
WHERE a.created_at >= NOW() - INTERVAL '90 days'
GROUP BY skill
HAVING COUNT(*) >= 5  -- Only include skills that appear at least 5 times
ORDER BY frequency DESC;

-- Index for popular skills
CREATE INDEX IF NOT EXISTS idx_popular_skills_mv_frequency 
    ON popular_skills_mv(frequency DESC, success_rate DESC);

-- Schedule automatic refresh of materialized views
-- This would typically be done via a job scheduler, but we'll create the function
CREATE OR REPLACE FUNCTION refresh_all_dashboard_views() RETURNS void AS $$
BEGIN
    PERFORM refresh_user_job_stats();
    PERFORM refresh_job_performance_metrics();
    REFRESH MATERIALIZED VIEW CONCURRENTLY popular_skills_mv;
    
    -- Log completion
    INSERT INTO system_maintenance_log (operation, completed_at, details)
    VALUES ('refresh_all_dashboard_views', NOW(), 'All dashboard views refreshed successfully')
    ON CONFLICT (operation) DO UPDATE SET 
        completed_at = NOW(),
        details = 'All dashboard views refreshed successfully';
END;
$$ LANGUAGE plpgsql;

-- Initial refresh of all views
SELECT refresh_all_dashboard_views();