#!/bin/bash

# Script to simulate production database conditions locally
# This will help reproduce the SWATCH-3545 cascading deduction bug

set -e

echo "🏭 Simulating Production Database Conditions for SWATCH-3545 Bug Reproduction"
echo "============================================================================="

# Configuration paths
PG_CONFIG="/var/lib/pgsql/data/userdata/postgresql.conf"
PG_BACKUP="/var/lib/pgsql/data/userdata/postgresql.conf.backup-$(date +%Y%m%d-%H%M%S)"

# Production-like settings (based on stage environment analysis)
PROD_MAX_CONNECTIONS=50    # Reduced from 5000 to create connection pressure (even lower than prod's 181)
PROD_SHARED_BUFFERS=128MB  # Increased from 32MB to simulate higher memory usage
PROD_WORK_MEM=8MB         # Increased from 4MB to simulate production memory settings

echo "📋 Target Configuration (Production-like):"
echo "  - max_connections: $PROD_MAX_CONNECTIONS (was 5000)"
echo "  - shared_buffers: $PROD_SHARED_BUFFERS (was 32MB)"  
echo "  - work_mem: $PROD_WORK_MEM (was 4MB)"
echo ""

# Function to backup original config
backup_config() {
    echo "💾 Backing up original PostgreSQL configuration..."
    if [[ -f "$PG_CONFIG" ]]; then
        sudo cp "$PG_CONFIG" "$PG_BACKUP"
        echo "✅ Backup created: $PG_BACKUP"
    else
        echo "❌ ERROR: PostgreSQL config file not found at $PG_CONFIG"
        exit 1
    fi
}

# Function to apply production-like settings
apply_production_config() {
    echo ""
    echo "🔧 Applying production-like database settings..."
    
    # Create temporary config with production-like settings
    sudo tee "$PG_CONFIG.production" > /dev/null <<EOF
# Production-like settings for SWATCH-3545 bug reproduction
# Generated on $(date)

# Connection Settings
max_connections = $PROD_MAX_CONNECTIONS
shared_buffers = $PROD_SHARED_BUFFERS
work_mem = $PROD_WORK_MEM

# Keep existing settings but override key ones
listen_addresses = '*'
port = 5432

# Memory settings to simulate production pressure
effective_cache_size = 256MB
maintenance_work_mem = 64MB

# Logging for debugging
log_statement = 'all'
log_min_duration_statement = 100ms
log_connections = on
log_disconnections = on

# Transaction isolation (same as production)
default_transaction_isolation = 'read committed'

# Connection pooling simulation
tcp_keepalives_idle = 600
tcp_keepalives_interval = 30
tcp_keepalives_count = 3
EOF

    # Replace the config file
    sudo cp "$PG_CONFIG.production" "$PG_CONFIG"
    echo "✅ Production-like configuration applied"
}

# Function to restart PostgreSQL
restart_postgresql() {
    echo ""
    echo "🔄 Restarting PostgreSQL to apply new configuration..."
    
    # Try different ways to restart PostgreSQL depending on the system
    if command -v systemctl &> /dev/null; then
        sudo systemctl restart postgresql
        echo "✅ PostgreSQL restarted via systemctl"
    elif command -v service &> /dev/null; then
        sudo service postgresql restart
        echo "✅ PostgreSQL restarted via service"
    else
        echo "❌ ERROR: Could not restart PostgreSQL. Please restart manually."
        exit 1
    fi
    
    # Wait for PostgreSQL to be ready
    echo "⏳ Waiting for PostgreSQL to be ready..."
    sleep 5
    
    # Test connection
    if psql -h localhost -U rhsm-subscriptions rhsm-subscriptions -c "SELECT 1;" > /dev/null 2>&1; then
        echo "✅ PostgreSQL is ready"
    else
        echo "❌ ERROR: PostgreSQL connection failed after restart"
        exit 1
    fi
}

# Function to verify new settings
verify_config() {
    echo ""
    echo "🔍 Verifying new database configuration..."
    
    psql -h localhost -U rhsm-subscriptions rhsm-subscriptions -c "
    SELECT name, setting, unit 
    FROM pg_settings 
    WHERE name IN ('max_connections', 'shared_buffers', 'work_mem', 'effective_cache_size')
    ORDER BY name;"
    
    echo ""
    echo "📊 Configuration verification complete"
}

# Function to restore original config
restore_config() {
    echo ""
    echo "🔄 Restoring original PostgreSQL configuration..."
    
    if [[ -f "$PG_BACKUP" ]]; then
        sudo cp "$PG_BACKUP" "$PG_CONFIG"
        echo "✅ Original configuration restored from: $PG_BACKUP"
        restart_postgresql
    else
        echo "❌ ERROR: Backup file not found. Cannot restore original configuration."
        exit 1
    fi
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [apply|restore|verify]"
    echo ""
    echo "Commands:"
    echo "  apply   - Apply production-like database configuration"
    echo "  restore - Restore original database configuration"
    echo "  verify  - Verify current database configuration"
    echo ""
    echo "Example workflow:"
    echo "  1. $0 apply     # Apply production-like settings"
    echo "  2. Run your tests to reproduce the bug"
    echo "  3. $0 restore   # Restore original settings when done"
}

# Main execution
case "${1:-apply}" in
    "apply")
        backup_config
        apply_production_config
        restart_postgresql
        verify_config
        echo ""
        echo "🎯 Production-like database configuration applied!"
        echo "   You can now run your tests to try reproducing the SWATCH-3545 bug."
        echo "   When done, run: $0 restore"
        ;;
    "restore")
        restore_config
        verify_config
        echo ""
        echo "✅ Original database configuration restored!"
        ;;
    "verify")
        verify_config
        ;;
    *)
        show_usage
        exit 1
        ;;
esac