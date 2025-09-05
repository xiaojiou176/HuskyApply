-- Create subscription_plans table
CREATE TABLE subscription_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    price_monthly DECIMAL(10,2) NOT NULL,
    price_yearly DECIMAL(10,2),
    jobs_per_month INTEGER,  -- NULL means unlimited
    templates_limit INTEGER, -- NULL means unlimited
    batch_jobs_limit INTEGER, -- NULL means unlimited
    ai_models_access TEXT, -- JSON array of allowed models
    priority_processing BOOLEAN DEFAULT FALSE,
    api_access BOOLEAN DEFAULT FALSE,
    team_collaboration BOOLEAN DEFAULT FALSE,
    white_label BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    stripe_price_id_monthly VARCHAR(100),
    stripe_price_id_yearly VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create user_subscriptions table
CREATE TABLE user_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subscription_plan_id UUID NOT NULL REFERENCES subscription_plans(id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    billing_cycle VARCHAR(10) NOT NULL DEFAULT 'MONTHLY',
    stripe_subscription_id VARCHAR(100),
    stripe_customer_id VARCHAR(100),
    current_period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    current_period_end TIMESTAMP WITH TIME ZONE NOT NULL,
    jobs_used_this_period INTEGER DEFAULT 0,
    cancel_at_period_end BOOLEAN DEFAULT FALSE,
    trial_end TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMP WITH TIME ZONE
);

-- Create indexes
CREATE INDEX idx_subscription_plans_active ON subscription_plans(is_active);
CREATE INDEX idx_user_subscriptions_user_id ON user_subscriptions(user_id);
CREATE INDEX idx_user_subscriptions_status ON user_subscriptions(status);
CREATE INDEX idx_user_subscriptions_stripe_subscription_id ON user_subscriptions(stripe_subscription_id);
CREATE INDEX idx_user_subscriptions_current_period_end ON user_subscriptions(current_period_end);

-- Insert default subscription plans
INSERT INTO subscription_plans (name, description, price_monthly, price_yearly, jobs_per_month, templates_limit, batch_jobs_limit, ai_models_access, priority_processing, api_access, team_collaboration, white_label) VALUES
('Free', 'Perfect for getting started with AI-powered applications', 0.00, 0.00, 5, 3, 1, '["gpt-3.5-turbo"]', FALSE, FALSE, FALSE, FALSE),
('Pro', 'For professionals who need more power and flexibility', 19.99, 199.99, 100, 50, 10, '["gpt-3.5-turbo", "gpt-4o", "gpt-4-turbo"]', TRUE, FALSE, FALSE, FALSE),
('Team', 'For teams that need collaboration and advanced features', 49.99, 499.99, 500, null, 50, '["gpt-3.5-turbo", "gpt-4o", "gpt-4-turbo", "claude-3-5-sonnet-20241022", "claude-3-opus-20240229"]', TRUE, TRUE, TRUE, FALSE),
('Enterprise', 'For organizations requiring white-label solutions and unlimited usage', 199.99, 1999.99, null, null, null, '["gpt-3.5-turbo", "gpt-4o", "gpt-4-turbo", "claude-3-5-sonnet-20241022", "claude-3-opus-20240229", "claude-3-haiku-20240307"]', TRUE, TRUE, TRUE, TRUE);

-- Function to automatically assign free plan to new users
CREATE OR REPLACE FUNCTION assign_free_plan_to_new_user()
RETURNS TRIGGER AS $$
DECLARE
    free_plan_id UUID;
BEGIN
    -- Get the free plan ID
    SELECT id INTO free_plan_id FROM subscription_plans WHERE name = 'Free' LIMIT 1;
    
    IF free_plan_id IS NOT NULL THEN
        -- Create a subscription for the new user with 7-day trial
        INSERT INTO user_subscriptions (
            user_id, 
            subscription_plan_id, 
            status,
            current_period_start,
            current_period_end,
            trial_end
        ) VALUES (
            NEW.id, 
            free_plan_id, 
            'ACTIVE',
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP + INTERVAL '30 days',
            CURRENT_TIMESTAMP + INTERVAL '7 days'
        );
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to assign free plan to new users
CREATE TRIGGER trigger_assign_free_plan
    AFTER INSERT ON users
    FOR EACH ROW
    EXECUTE FUNCTION assign_free_plan_to_new_user();

-- Function to increment job usage when a job is created
CREATE OR REPLACE FUNCTION increment_job_usage()
RETURNS TRIGGER AS $$
BEGIN
    -- Increment job usage for the user's current subscription
    UPDATE user_subscriptions 
    SET jobs_used_this_period = jobs_used_this_period + 1,
        updated_at = CURRENT_TIMESTAMP
    WHERE user_id = NEW.user_id 
        AND status = 'ACTIVE'
        AND current_period_end > CURRENT_TIMESTAMP;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to increment job usage when jobs are created
CREATE TRIGGER trigger_increment_job_usage
    AFTER INSERT ON jobs
    FOR EACH ROW
    EXECUTE FUNCTION increment_job_usage();