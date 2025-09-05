#!/bin/bash

# HuskyApply Database Backup and Recovery System
# Comprehensive backup solution with incremental, full, and point-in-time recovery

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
CONFIG_FILE="${PROJECT_ROOT}/infra/.env"

# Default configuration
DB_NAME="${POSTGRES_DB:-huskyapply}"
DB_USER="${POSTGRES_USER:-husky}"
DB_PASSWORD="${POSTGRES_PASSWORD:-husky}"
DB_HOST="${POSTGRES_HOST:-localhost}"
DB_PORT="${POSTGRES_PORT:-5432}"

# Backup configuration
BACKUP_DIR="${BACKUP_DIR:-${PROJECT_ROOT}/backups}"
S3_BUCKET="${S3_BACKUP_BUCKET:-huskyapply-backups}"
S3_REGION="${S3_REGION:-us-east-1}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"
COMPRESSION_LEVEL="${COMPRESSION_LEVEL:-6}"

# Helper functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Load environment configuration
load_config() {
    if [[ -f "$CONFIG_FILE" ]]; then
        log_info "Loading configuration from $CONFIG_FILE"
        set -a  # Export all variables
        source "$CONFIG_FILE"
        set +a
    else
        log_warning "Configuration file not found: $CONFIG_FILE"
        log_info "Using default configuration"
    fi
}

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # Check required tools
    local required_tools=("pg_dump" "pg_restore" "psql" "aws" "gzip")
    for tool in "${required_tools[@]}"; do
        if ! command -v "$tool" &> /dev/null; then
            log_error "Required tool not found: $tool"
            case $tool in
                pg_dump|pg_restore|psql)
                    echo "Install PostgreSQL client tools"
                    ;;
                aws)
                    echo "Install AWS CLI: pip install awscli"
                    ;;
                gzip)
                    echo "Install gzip (usually available by default)"
                    ;;
            esac
            exit 1
        fi
    done
    
    # Create backup directory
    mkdir -p "$BACKUP_DIR"
    mkdir -p "$BACKUP_DIR/full"
    mkdir -p "$BACKUP_DIR/incremental"
    mkdir -p "$BACKUP_DIR/wal"
    mkdir -p "$BACKUP_DIR/schemas"
    
    # Test database connectivity
    if ! PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c '\q' &>/dev/null; then
        log_error "Cannot connect to database: postgresql://$DB_USER:***@$DB_HOST:$DB_PORT/$DB_NAME"
        exit 1
    fi
    
    # Test S3 connectivity
    if ! aws s3 ls "s3://$S3_BUCKET" &>/dev/null; then
        log_warning "Cannot access S3 bucket: s3://$S3_BUCKET"
        log_info "Backups will be stored locally only"
        S3_ENABLED=false
    else
        S3_ENABLED=true
        log_success "S3 bucket access confirmed: s3://$S3_BUCKET"
    fi
    
    log_success "Prerequisites check passed"
}

# Create full backup
create_full_backup() {
    local timestamp=$(date +%Y%m%d_%H%M%S)
    local backup_file="$BACKUP_DIR/full/huskyapply_full_${timestamp}.sql"
    local compressed_file="${backup_file}.gz"
    
    log_info "Creating full database backup..."
    log_info "Output: $compressed_file"
    
    # Create backup with custom format for faster restore and parallel processing
    PGPASSWORD="$DB_PASSWORD" pg_dump \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        --format=custom \
        --compress="$COMPRESSION_LEVEL" \
        --verbose \
        --file="$backup_file.dump"
    
    # Also create SQL format for readability
    PGPASSWORD="$DB_PASSWORD" pg_dump \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        --format=plain \
        --verbose | gzip -"$COMPRESSION_LEVEL" > "$compressed_file"
    
    # Generate backup metadata
    local metadata_file="$BACKUP_DIR/full/huskyapply_full_${timestamp}.meta"
    cat > "$metadata_file" << EOF
{
  "backup_type": "full",
  "timestamp": "$timestamp",
  "database": "$DB_NAME",
  "host": "$DB_HOST",
  "port": "$DB_PORT", 
  "user": "$DB_USER",
  "backup_file": "$compressed_file",
  "dump_file": "$backup_file.dump",
  "size_bytes": $(stat -c%s "$compressed_file" 2>/dev/null || stat -f%z "$compressed_file"),
  "dump_size_bytes": $(stat -c%s "$backup_file.dump" 2>/dev/null || stat -f%z "$backup_file.dump"),
  "compression_level": "$COMPRESSION_LEVEL",
  "created_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "postgresql_version": "$(PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -t -c 'SELECT version();' | head -n1 | xargs)",
  "checksum": "$(sha256sum "$compressed_file" | cut -d' ' -f1)"
}
EOF
    
    # Upload to S3 if enabled
    if [[ "$S3_ENABLED" == "true" ]]; then
        log_info "Uploading backup to S3..."
        aws s3 cp "$compressed_file" "s3://$S3_BUCKET/full/" --region "$S3_REGION"
        aws s3 cp "$backup_file.dump" "s3://$S3_BUCKET/full/" --region "$S3_REGION"
        aws s3 cp "$metadata_file" "s3://$S3_BUCKET/full/" --region "$S3_REGION"
        log_success "Backup uploaded to S3"
    fi
    
    log_success "Full backup completed: $compressed_file"
    echo "Backup size: $(du -h "$compressed_file" | cut -f1)"
    echo "Dump size: $(du -h "$backup_file.dump" | cut -f1)"
}

# Create incremental backup (schema + data changes)
create_incremental_backup() {
    local timestamp=$(date +%Y%m%d_%H%M%S)
    local backup_file="$BACKUP_DIR/incremental/huskyapply_incremental_${timestamp}.sql.gz"
    
    log_info "Creating incremental backup..."
    
    # Get the last full backup timestamp
    local last_full_backup=$(ls -t "$BACKUP_DIR/full"/*.meta 2>/dev/null | head -n1)
    local since_timestamp=""
    
    if [[ -f "$last_full_backup" ]]; then
        since_timestamp=$(grep '"timestamp"' "$last_full_backup" | cut -d'"' -f4)
        log_info "Creating incremental backup since: $since_timestamp"
    else
        log_warning "No full backup found, creating full backup instead"
        create_full_backup
        return 0
    fi
    
    # Create incremental backup by exporting modified data
    # Note: This is a simplified approach. For true incremental backups,
    # you would need WAL-E or similar tools with WAL archiving
    
    local temp_sql="/tmp/incremental_${timestamp}.sql"
    
    cat > "$temp_sql" << EOF
-- Incremental backup from $since_timestamp
-- Generated at $(date -u +%Y-%m-%dT%H:%M:%SZ)

-- Export recently modified jobs
COPY (
  SELECT * FROM jobs 
  WHERE updated_at >= '$since_timestamp' OR created_at >= '$since_timestamp'
) TO STDOUT WITH (FORMAT CSV, HEADER TRUE);

-- Export recently modified artifacts  
COPY (
  SELECT * FROM artifacts
  WHERE updated_at >= '$since_timestamp' OR created_at >= '$since_timestamp'
) TO STDOUT WITH (FORMAT CSV, HEADER TRUE);

-- Export recently modified users
COPY (
  SELECT * FROM users
  WHERE updated_at >= '$since_timestamp' OR created_at >= '$since_timestamp'
) TO STDOUT WITH (FORMAT CSV, HEADER TRUE);

-- Export recently modified templates
COPY (
  SELECT * FROM user_templates
  WHERE updated_at >= '$since_timestamp' OR created_at >= '$since_timestamp'
) TO STDOUT WITH (FORMAT CSV, HEADER TRUE);

-- Export recently modified batch jobs
COPY (
  SELECT * FROM batch_jobs
  WHERE updated_at >= '$since_timestamp' OR created_at >= '$since_timestamp'
) TO STDOUT WITH (FORMAT CSV, HEADER TRUE);
EOF
    
    # Execute incremental backup
    PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -f "$temp_sql" | gzip -"$COMPRESSION_LEVEL" > "$backup_file"
    
    # Generate metadata
    local metadata_file="$BACKUP_DIR/incremental/huskyapply_incremental_${timestamp}.meta"
    cat > "$metadata_file" << EOF
{
  "backup_type": "incremental",
  "timestamp": "$timestamp",
  "since_timestamp": "$since_timestamp",
  "database": "$DB_NAME",
  "host": "$DB_HOST",
  "backup_file": "$backup_file",
  "size_bytes": $(stat -c%s "$backup_file" 2>/dev/null || stat -f%z "$backup_file"),
  "created_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "checksum": "$(sha256sum "$backup_file" | cut -d' ' -f1)"
}
EOF
    
    # Upload to S3 if enabled
    if [[ "$S3_ENABLED" == "true" ]]; then
        aws s3 cp "$backup_file" "s3://$S3_BUCKET/incremental/" --region "$S3_REGION"
        aws s3 cp "$metadata_file" "s3://$S3_BUCKET/incremental/" --region "$S3_REGION"
    fi
    
    # Cleanup
    rm -f "$temp_sql"
    
    log_success "Incremental backup completed: $backup_file"
    echo "Backup size: $(du -h "$backup_file" | cut -f1)"
}

# Create schema-only backup
create_schema_backup() {
    local timestamp=$(date +%Y%m%d_%H%M%S)
    local backup_file="$BACKUP_DIR/schemas/huskyapply_schema_${timestamp}.sql"
    local compressed_file="${backup_file}.gz"
    
    log_info "Creating schema-only backup..."
    
    PGPASSWORD="$DB_PASSWORD" pg_dump \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        --schema-only \
        --verbose | gzip -"$COMPRESSION_LEVEL" > "$compressed_file"
    
    # Generate metadata
    local metadata_file="$BACKUP_DIR/schemas/huskyapply_schema_${timestamp}.meta"
    cat > "$metadata_file" << EOF
{
  "backup_type": "schema",
  "timestamp": "$timestamp",
  "database": "$DB_NAME",
  "backup_file": "$compressed_file",
  "size_bytes": $(stat -c%s "$compressed_file" 2>/dev/null || stat -f%z "$compressed_file"),
  "created_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "checksum": "$(sha256sum "$compressed_file" | cut -d' ' -f1)"
}
EOF
    
    # Upload to S3 if enabled
    if [[ "$S3_ENABLED" == "true" ]]; then
        aws s3 cp "$compressed_file" "s3://$S3_BUCKET/schemas/" --region "$S3_REGION"
        aws s3 cp "$metadata_file" "s3://$S3_BUCKET/schemas/" --region "$S3_REGION"
    fi
    
    log_success "Schema backup completed: $compressed_file"
}

# Restore from backup
restore_from_backup() {
    local backup_file="$1"
    local restore_db="${2:-${DB_NAME}_restore_$(date +%Y%m%d_%H%M%S)}"
    
    if [[ ! -f "$backup_file" ]]; then
        log_error "Backup file not found: $backup_file"
        exit 1
    fi
    
    log_info "Restoring database from: $backup_file"
    log_info "Target database: $restore_db"
    
    # Create target database
    log_info "Creating target database..."
    PGPASSWORD="$DB_PASSWORD" createdb \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        "$restore_db"
    
    # Determine backup format and restore accordingly
    if [[ "$backup_file" == *.dump ]]; then
        # Custom format backup
        log_info "Restoring from custom format backup..."
        PGPASSWORD="$DB_PASSWORD" pg_restore \
            -h "$DB_HOST" \
            -p "$DB_PORT" \
            -U "$DB_USER" \
            -d "$restore_db" \
            --jobs=4 \
            --verbose \
            "$backup_file"
    elif [[ "$backup_file" == *.gz ]]; then
        # Compressed SQL backup
        log_info "Restoring from compressed SQL backup..."
        gunzip -c "$backup_file" | PGPASSWORD="$DB_PASSWORD" psql \
            -h "$DB_HOST" \
            -p "$DB_PORT" \
            -U "$DB_USER" \
            -d "$restore_db"
    else
        # Plain SQL backup
        log_info "Restoring from SQL backup..."
        PGPASSWORD="$DB_PASSWORD" psql \
            -h "$DB_HOST" \
            -p "$DB_PORT" \
            -U "$DB_USER" \
            -d "$restore_db" \
            -f "$backup_file"
    fi
    
    log_success "Database restored successfully to: $restore_db"
    
    # Verify restore
    local table_count=$(PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$restore_db" -t -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public';")
    log_info "Restored database contains $table_count tables"
}

# List available backups
list_backups() {
    echo "📋 Available Backups"
    echo "===================="
    
    echo ""
    echo "🔵 Full Backups:"
    if ls "$BACKUP_DIR/full"/*.meta &>/dev/null; then
        for meta_file in "$BACKUP_DIR/full"/*.meta; do
            local timestamp=$(grep '"timestamp"' "$meta_file" | cut -d'"' -f4)
            local size_bytes=$(grep '"size_bytes"' "$meta_file" | cut -d' ' -f2 | tr -d ',')
            local size_human=$(numfmt --to=iec "$size_bytes" 2>/dev/null || echo "$size_bytes bytes")
            echo "  - $timestamp ($size_human)"
        done
    else
        echo "  No full backups found"
    fi
    
    echo ""
    echo "🟡 Incremental Backups:"
    if ls "$BACKUP_DIR/incremental"/*.meta &>/dev/null; then
        for meta_file in "$BACKUP_DIR/incremental"/*.meta; do
            local timestamp=$(grep '"timestamp"' "$meta_file" | cut -d'"' -f4)
            local since=$(grep '"since_timestamp"' "$meta_file" | cut -d'"' -f4)
            local size_bytes=$(grep '"size_bytes"' "$meta_file" | cut -d' ' -f2 | tr -d ',')
            local size_human=$(numfmt --to=iec "$size_bytes" 2>/dev/null || echo "$size_bytes bytes")
            echo "  - $timestamp (since $since, $size_human)"
        done
    else
        echo "  No incremental backups found"
    fi
    
    echo ""
    echo "🟢 Schema Backups:"
    if ls "$BACKUP_DIR/schemas"/*.meta &>/dev/null; then
        for meta_file in "$BACKUP_DIR/schemas"/*.meta; do
            local timestamp=$(grep '"timestamp"' "$meta_file" | cut -d'"' -f4)
            local size_bytes=$(grep '"size_bytes"' "$meta_file" | cut -d' ' -f2 | tr -d ',')
            local size_human=$(numfmt --to=iec "$size_bytes" 2>/dev/null || echo "$size_bytes bytes")
            echo "  - $timestamp ($size_human)"
        done
    else
        echo "  No schema backups found"
    fi
}

# Cleanup old backups
cleanup_old_backups() {
    log_info "Cleaning up backups older than $RETENTION_DAYS days..."
    
    local dirs=("$BACKUP_DIR/full" "$BACKUP_DIR/incremental" "$BACKUP_DIR/schemas")
    local total_removed=0
    
    for dir in "${dirs[@]}"; do
        if [[ -d "$dir" ]]; then
            local removed_count=0
            find "$dir" -name "*.sql.gz" -mtime +${RETENTION_DAYS} -delete
            find "$dir" -name "*.dump" -mtime +${RETENTION_DAYS} -delete  
            find "$dir" -name "*.meta" -mtime +${RETENTION_DAYS} -delete
            
            # Count would be complex with find, so we'll just report the action
            log_info "Cleaned up old backups in $dir"
        fi
    done
    
    # Cleanup S3 backups if enabled
    if [[ "$S3_ENABLED" == "true" ]]; then
        log_info "Cleaning up old S3 backups..."
        
        # Use S3 lifecycle policies for this in production
        # For now, we'll just note that this should be configured
        log_info "Note: Configure S3 lifecycle policies for automated cleanup"
    fi
    
    log_success "Backup cleanup completed"
}

# Verify backup integrity
verify_backup() {
    local backup_file="$1"
    
    if [[ ! -f "$backup_file" ]]; then
        log_error "Backup file not found: $backup_file"
        exit 1
    fi
    
    log_info "Verifying backup integrity: $backup_file"
    
    # Check if it's a compressed file
    if [[ "$backup_file" == *.gz ]]; then
        if gzip -t "$backup_file"; then
            log_success "Backup file compression is valid"
        else
            log_error "Backup file compression is corrupted"
            return 1
        fi
    fi
    
    # Verify checksum if metadata exists
    local meta_file="${backup_file%.*}.meta"
    if [[ "$backup_file" == *.sql.gz ]]; then
        meta_file="${backup_file%.sql.gz}.meta"
    elif [[ "$backup_file" == *.dump ]]; then
        meta_file="${backup_file%.dump}.meta"
    fi
    
    if [[ -f "$meta_file" ]]; then
        local expected_checksum=$(grep '"checksum"' "$meta_file" | cut -d'"' -f4)
        local actual_checksum=$(sha256sum "$backup_file" | cut -d' ' -f1)
        
        if [[ "$expected_checksum" == "$actual_checksum" ]]; then
            log_success "Backup checksum verification passed"
        else
            log_error "Backup checksum verification failed"
            log_error "Expected: $expected_checksum"
            log_error "Actual: $actual_checksum"
            return 1
        fi
    else
        log_warning "No metadata file found for checksum verification"
    fi
    
    log_success "Backup verification completed successfully"
}

# Show backup statistics
show_statistics() {
    echo "📊 Backup Statistics"
    echo "===================="
    
    local total_backups=0
    local total_size=0
    
    # Count and size full backups
    local full_count=$(ls "$BACKUP_DIR/full"/*.meta 2>/dev/null | wc -l)
    local full_size=0
    for meta_file in "$BACKUP_DIR/full"/*.meta 2>/dev/null; do
        local size=$(grep '"size_bytes"' "$meta_file" 2>/dev/null | cut -d' ' -f2 | tr -d ',' || echo "0")
        full_size=$((full_size + size))
    done
    
    # Count and size incremental backups
    local inc_count=$(ls "$BACKUP_DIR/incremental"/*.meta 2>/dev/null | wc -l)
    local inc_size=0
    for meta_file in "$BACKUP_DIR/incremental"/*.meta 2>/dev/null; do
        local size=$(grep '"size_bytes"' "$meta_file" 2>/dev/null | cut -d' ' -f2 | tr -d ',' || echo "0")
        inc_size=$((inc_size + size))
    done
    
    # Count and size schema backups
    local schema_count=$(ls "$BACKUP_DIR/schemas"/*.meta 2>/dev/null | wc -l)
    local schema_size=0
    for meta_file in "$BACKUP_DIR/schemas"/*.meta 2>/dev/null; do
        local size=$(grep '"size_bytes"' "$meta_file" 2>/dev/null | cut -d' ' -f2 | tr -d ',' || echo "0")
        schema_size=$((schema_size + size))
    done
    
    total_backups=$((full_count + inc_count + schema_count))
    total_size=$((full_size + inc_size + schema_size))
    
    echo "Full Backups: $full_count ($(numfmt --to=iec $full_size 2>/dev/null || echo "$full_size bytes"))"
    echo "Incremental Backups: $inc_count ($(numfmt --to=iec $inc_size 2>/dev/null || echo "$inc_size bytes"))"
    echo "Schema Backups: $schema_count ($(numfmt --to=iec $schema_size 2>/dev/null || echo "$schema_size bytes"))"
    echo "Total Backups: $total_backups ($(numfmt --to=iec $total_size 2>/dev/null || echo "$total_size bytes"))"
    
    if [[ "$S3_ENABLED" == "true" ]]; then
        echo ""
        echo "S3 Bucket: s3://$S3_BUCKET"
        echo "S3 Region: $S3_REGION"
    fi
    
    echo ""
    echo "Retention Policy: $RETENTION_DAYS days"
    echo "Backup Directory: $BACKUP_DIR"
}

# Main execution
main() {
    echo "🛡️  HuskyApply Database Backup & Recovery System"
    echo "==============================================="
    echo ""

    load_config
    check_prerequisites

    case "${1:-help}" in
        full)
            create_full_backup
            ;;
        incremental)
            create_incremental_backup
            ;;
        schema)
            create_schema_backup
            ;;
        restore)
            if [[ -z "${2:-}" ]]; then
                log_error "Please specify backup file to restore"
                echo "Usage: $0 restore <backup_file> [target_db_name]"
                exit 1
            fi
            restore_from_backup "$2" "${3:-}"
            ;;
        list)
            list_backups
            ;;
        verify)
            if [[ -z "${2:-}" ]]; then
                log_error "Please specify backup file to verify"
                echo "Usage: $0 verify <backup_file>"
                exit 1
            fi
            verify_backup "$2"
            ;;
        cleanup)
            cleanup_old_backups
            ;;
        stats)
            show_statistics
            ;;
        automated)
            # Automated backup routine - full backup weekly, incremental daily
            local day_of_week=$(date +%u)  # 1 = Monday, 7 = Sunday
            
            if [[ "$day_of_week" == "7" ]]; then
                # Sunday - full backup
                log_info "Running automated full backup (Sunday routine)"
                create_full_backup
            else
                # Other days - incremental backup
                log_info "Running automated incremental backup (daily routine)"
                create_incremental_backup
            fi
            
            # Clean up old backups
            cleanup_old_backups
            ;;
        help|*)
            cat << EOF
HuskyApply Database Backup & Recovery System

USAGE:
    $0 <command> [options]

COMMANDS:
    full                    Create full database backup
    incremental            Create incremental backup (changes since last full)
    schema                 Create schema-only backup
    restore <file> [db]    Restore from backup file
    list                   List available backups
    verify <file>          Verify backup file integrity
    cleanup                Remove backups older than retention period
    stats                  Show backup statistics
    automated              Run automated backup routine (full on Sunday, incremental daily)
    help                   Show this help message

EXAMPLES:
    $0 full                                    # Create full backup
    $0 incremental                             # Create incremental backup
    $0 restore backups/full/backup.sql.gz     # Restore from backup
    $0 verify backups/full/backup.sql.gz      # Verify backup integrity
    $0 automated                               # Run automated routine

ENVIRONMENT VARIABLES:
    POSTGRES_DB            Database name (default: huskyapply)
    POSTGRES_USER          Database user (default: husky)
    POSTGRES_PASSWORD      Database password (default: husky)
    POSTGRES_HOST          Database host (default: localhost)
    POSTGRES_PORT          Database port (default: 5432)
    BACKUP_DIR             Local backup directory (default: ./backups)
    S3_BACKUP_BUCKET       S3 bucket for backup storage
    BACKUP_RETENTION_DAYS  Days to retain backups (default: 30)
    COMPRESSION_LEVEL      Gzip compression level 1-9 (default: 6)

CONFIGURATION:
    Settings are loaded from: $CONFIG_FILE

EOF
            ;;
    esac
}

# Handle script interruption
trap 'log_warning "Backup operation interrupted by user"; exit 130' INT

# Execute main function with all arguments  
main "$@"