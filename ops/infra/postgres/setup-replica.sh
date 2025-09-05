#!/bin/bash
# Setup PostgreSQL Read Replica

set -e

echo "Setting up PostgreSQL Read Replica..."

# Wait for master to be ready
echo "Waiting for master database to be ready..."
until pg_isready -h "$POSTGRES_MASTER_SERVICE" -p 5432 -U postgres; do
    echo "Waiting for master database..."
    sleep 2
done

echo "Master database is ready. Setting up replica..."

# Remove existing data directory if it exists
if [ -d "/var/lib/postgresql/data" ] && [ "$(ls -A /var/lib/postgresql/data 2>/dev/null)" ]; then
    echo "Cleaning existing data directory..."
    rm -rf /var/lib/postgresql/data/*
fi

# Create base backup from master
echo "Creating base backup from master..."
PGPASSWORD='replicator_password' pg_basebackup -h "$POSTGRES_MASTER_SERVICE" -D /var/lib/postgresql/data -U replicator -v -P -W -R

# Set proper permissions
chown -R postgres:postgres /var/lib/postgresql/data
chmod 0700 /var/lib/postgresql/data

# Create recovery configuration
echo "Configuring replica for streaming replication..."
cat > /var/lib/postgresql/data/postgresql.auto.conf <<EOF
# Replica-specific configuration
hot_standby = on
primary_conninfo = 'host=$POSTGRES_MASTER_SERVICE port=5432 user=replicator password=replicator_password application_name=$(hostname)'
primary_slot_name = '$(hostname)_slot'
EOF

# Create standby signal file
touch /var/lib/postgresql/data/standby.signal

# Start PostgreSQL
echo "Starting PostgreSQL replica..."
exec postgres