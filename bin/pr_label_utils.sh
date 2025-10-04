#!/bin/bash

# Label utilities for GitHub Actions workflows
# Usage: source bin/pr_label_utils.sh

# Service groups by team
SWATCH_1=("swatch-metrics" "swatch-metrics-hbi" "swatch-tally" "swatch-system-conduit")
SWATCH_2=("swatch-billable-usage" "swatch-contracts" "swatch-producer-azure" "swatch-producer-aws" "swatch-utilization")

# All services (combination of both teams)
ALL_SERVICES=("${SWATCH_1[@]}" "${SWATCH_2[@]}")

# Get current PR labels
get_current_labels() {
    local pr_number=$1
    gh pr view "$pr_number" --json labels --jq '.labels[].name' | tr '\n' ' '
}

# Check if label exists in current labels
has_label() {
    local label=$1
    local current_labels=$2
    [[ " $current_labels " =~ " $label " ]]
}

# Add label if not present
add_label_if_needed() {
    local pr_number=$1
    local label=$2
    local current_labels=$3
    
    if ! has_label "$label" "$current_labels"; then
        echo "Adding label: $label"
        if ! gh pr edit "$pr_number" --add-label "$label"; then
            echo "Error: Failed to add label $label to PR $pr_number"
            return 1
        fi
        echo "Successfully added label: $label"
    else
        echo "Label already present: $label"
    fi
    return 0
}

# Remove label if present
remove_label_if_needed() {
    local pr_number=$1
    local label=$2
    local current_labels=$3
    
    if has_label "$label" "$current_labels"; then
        echo "Removing label: $label"
        if ! gh pr edit "$pr_number" --remove-label "$label"; then
            echo "Error: Failed to remove label $label from PR $pr_number"
            return 1
        fi
        echo "Successfully removed label: $label"
    else
        echo "Label not present: $label"
    fi
    return 0
}

# Check if any service from a list is in modified services
is_team_modified() {
    local team_services=("$@")
    local modified_services_str="${MODIFIED_SERVICES[*]}"
    
    for service in "${team_services[@]}"; do
        if [[ " $modified_services_str " =~ " $service " ]]; then
            return 0  # true
        fi
    done
    return 1  # false
}

# Detect modified services based on changes and dependencies
detect_modified_services() {
    local mvnw_path="$1"
    local changed_modules="$2"
    
    echo "Detecting modified services (direct changes and dependencies)..."
    MODIFIED_SERVICES=()
    
    for SERVICE in "${ALL_SERVICES[@]}"; do
        # If the service itself changed, mark it as affected immediately
        if [[ " $changed_modules " =~ " $SERVICE " ]]; then
            MODIFIED_SERVICES+=("$SERVICE")
            continue
        fi
    
        # Check dependencies
        GRAPH_FILE="$SERVICE/target/dep-graph.dot"
        echo "Generating dependency graph for $SERVICE..."
        (cd "$SERVICE" && "$mvnw_path" dependency:tree -DoutputType=dot -DappendOutput=true -DoutputFile=target/dep-graph.dot -q)
      
        # Read the service's dependency graph line by line
        while IFS= read -r line; do
          if [[ "$line" == *"->"* ]]; then
            RIGHT=$(echo "$line" | cut -d'"' -f4 | cut -d':' -f2)
            for MOD in $changed_modules; do
              if [ "$RIGHT" = "$MOD" ]; then
                MODIFIED_SERVICES+=("$SERVICE")
                break 2
              fi
            done
          fi
        done < "$GRAPH_FILE"
    done
    
    echo "Modified services detected: ${MODIFIED_SERVICES[*]}"
}

# Manage individual service labels
manage_service_labels() {
    local pr_number=$1
    local current_labels=$2
    
    echo "Managing individual service labels..."
    echo "PR Number: $pr_number"
    echo "Current labels: '$current_labels'"
    echo "Modified services: '${MODIFIED_SERVICES[*]}'"
    
    for service in "${ALL_SERVICES[@]}"; do
        echo "Processing service: $service"
        local service_is_modified=false
        if [[ " ${MODIFIED_SERVICES[*]} " =~ " $service " ]]; then
            service_is_modified=true
            echo "  -> Service $service is modified"
        else
            echo "  -> Service $service is NOT modified"
        fi
        
        if [ "$service_is_modified" = true ]; then
            add_label_if_needed "$pr_number" "$service" "$current_labels"
        else
            remove_label_if_needed "$pr_number" "$service" "$current_labels"
        fi
    done
}

# Manage team labels
manage_team_labels() {
    local pr_number=$1
    local current_labels=$2
    
    echo "Managing team labels..."
    echo "PR Number: $pr_number"
    echo "Current labels: '$current_labels'"
    
    # SWATCH_1 team
    echo "Checking SWATCH_1 team..."
    if is_team_modified "${SWATCH_1[@]}"; then
        echo "SWATCH_1 team has modified services"
        add_label_if_needed "$pr_number" "swatch-1" "$current_labels"
    else
        echo "SWATCH_1 team has no modified services"
        remove_label_if_needed "$pr_number" "swatch-1" "$current_labels"
    fi
    
    # SWATCH_2 team
    echo "Checking SWATCH_2 team..."
    if is_team_modified "${SWATCH_2[@]}"; then
        echo "SWATCH_2 team has modified services"
        add_label_if_needed "$pr_number" "swatch-2" "$current_labels"
    else
        echo "SWATCH_2 team has no modified services"
        remove_label_if_needed "$pr_number" "swatch-2" "$current_labels"
    fi
    
    echo "Team label management completed"
}

# Remove all service and team labels
remove_all_labels() {
    local pr_number=$1
    local current_labels=$2
    
    echo "Removing all service and team labels if present"
    
    # Remove service labels
    for service in "${ALL_SERVICES[@]}"; do
        remove_label_if_needed "$pr_number" "$service" "$current_labels"
    done
    
    # Remove team labels
    remove_label_if_needed "$pr_number" "swatch-1" "$current_labels"
    remove_label_if_needed "$pr_number" "swatch-2" "$current_labels"
}