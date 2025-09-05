#!/bin/bash
# Initialize PostgreSQL Master for Replication

set -e

echo "Initializing PostgreSQL Master for Read-Write Splitting..."

# Create replication user
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- Create replication user
    CREATE USER replicator REPLICATION LOGIN ENCRYPTED PASSWORD 'replicator_password';
    
    -- Grant necessary permissions
    GRANT CONNECT ON DATABASE $POSTGRES_DB TO replicator;
    
    -- Create archive directory
    \! mkdir -p /var/lib/postgresql/archive
    \! chown postgres:postgres /var/lib/postgresql/archive
    
    -- Enable pg_stat_statements extension
    CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
    
    -- Create monitoring functions
    CREATE OR REPLACE FUNCTION get_replication_status()
    RETURNS TABLE(
        client_addr inet,
        client_hostname text,
        client_port integer,
        state text,
        sent_lsn pg_lsn,
        write_lsn pg_lsn,
        flush_lsn pg_lsn,
        replay_lsn pg_lsn,
        write_lag interval,
        flush_lag interval,
        replay_lag interval,
        sync_priority integer,
        sync_state text
    ) AS \$\$
    BEGIN
        RETURN QUERY
        SELECT 
            s.client_addr,
            s.client_hostname,
            s.client_port,
            s.state,
            s.sent_lsn,
            s.write_lsn,
            s.flush_lsn,
            s.replay_lsn,
            s.write_lag,
            s.flush_lag,
            s.replay_lag,
            s.sync_priority,
            s.sync_state
        FROM pg_stat_replication s;
    END;
    \$\$ LANGUAGE plpgsql SECURITY DEFINER;
    
    -- Create database health check function
    CREATE OR REPLACE FUNCTION database_health_check()
    RETURNS TABLE(
        metric_name text,
        metric_value numeric,
        metric_unit text,
        threshold_warning numeric,
        threshold_critical numeric,
        status text
    ) AS \$\$
    BEGIN
        -- Connection count check
        RETURN QUERY
        SELECT 
            'active_connections'::text,
            (SELECT count(*)::numeric FROM pg_stat_activity WHERE state = 'active'),
            'connections'::text,
            80::numeric,
            150::numeric,
            CASE 
                WHEN (SELECT count(*) FROM pg_stat_activity WHERE state = 'active') > 150 THEN 'CRITICAL'
                WHEN (SELECT count(*) FROM pg_stat_activity WHERE state = 'active') > 80 THEN 'WARNING'
                ELSE 'OK'
            END;
            
        -- Replication lag check
        RETURN QUERY
        SELECT 
            'max_replication_lag'::text,
            COALESCE(EXTRACT(EPOCH FROM MAX(replay_lag)), 0)::numeric,
            'seconds'::text,
            30::numeric,
            60::numeric,
            CASE 
                WHEN COALESCE(EXTRACT(EPOCH FROM MAX(replay_lag)), 0) > 60 THEN 'CRITICAL'
                WHEN COALESCE(EXTRACT(EPOCH FROM MAX(replay_lag)), 0) > 30 THEN 'WARNING'
                ELSE 'OK'
            END
        FROM pg_stat_replication;
    END;
    \$\$ LANGUAGE plpgsql SECURITY DEFINER;
    
    -- Grant permissions to application user
    GRANT EXECUTE ON FUNCTION get_replication_status() TO husky;
    GRANT EXECUTE ON FUNCTION database_health_check() TO husky;

EOSQL

echo "PostgreSQL Master replication setup complete!"

# Create replication slot for each replica
echo "Creating replication slots..."
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    SELECT pg_create_physical_replication_slot('replica1_slot');
    SELECT pg_create_physical_replication_slot('replica2_slot');
EOSQL

echo "Replication slots created successfully!"
echo "Master database is ready for replication."