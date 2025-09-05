-- Add model selection columns to batch_jobs table
ALTER TABLE batch_jobs 
ADD COLUMN model_provider VARCHAR(50) DEFAULT 'openai',
ADD COLUMN model_name VARCHAR(100);

-- Add indexes for model fields (if we need to query by model type)
CREATE INDEX idx_batch_jobs_model_provider ON batch_jobs(model_provider);