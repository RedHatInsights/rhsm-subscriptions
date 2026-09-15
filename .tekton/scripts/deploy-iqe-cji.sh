#!/bin/bash
# Deploy IQE CJI for SWATCH Konflux pipelines.
# Fork of cicd-tools konflux_scripts/deploy-iqe-cji.sh with quoted --env-var arguments
# so IBUTSU_DATA can contain space-separated pytest-ibutsu metadata.

set -euo pipefail

cleanup() {
    local pid="${1:?}"
    kill -9 "$pid" || :
}

main() {
    local ns="${1:?Namespace was not provided}"
    local ns_requester="${2:?Namespace requester name was not provided}"
    local component_name="${IQE_CJI_CLOWDAPP_NAME:-${BONFIRE_COMPONENT_NAME:-${COMPONENT_NAME:?Component name not provided}}}"
    local cji_name=$component_name

    local selenium="${IQE_SELENIUM:-false}"
    local playwright="${IQE_PLAYWRIGHT:-false}"
    local iqe_marker_expression="${IQE_MARKER_EXPRESSION}"
    local iqe_filter_expression="${IQE_FILTER_EXPRESSION}"
    local iqe_image_tag="${IQE_IMAGE_TAG}"
    local iqe_requirements="${IQE_REQUIREMENTS}"
    local iqe_requirements_priority="${IQE_REQUIREMENTS_PRIORITY}"
    local iqe_test_importance="${IQE_TEST_IMPORTANCE}"
    local iqe_plugins="${IQE_PLUGINS}"
    local iqe_env="${IQE_ENV:-clowder_smoke}"
    local iqe_env_vars="${IQE_ENV_VARS:-}"
    local iqe_cji_timeout="${IQE_CJI_TIMEOUT:-10m}"
    local iqe_parallel_enabled="${IQE_PARALLEL_ENABLED}"

    local selenium_arg=""
    if [[ "$selenium" == "true" ]]; then
        selenium_arg="--selenium"
    fi

    local playwright_arg=""
    if [[ "$playwright" == "true" ]]; then
        playwright_arg="--playwright"
    fi

    local iqe_env_var_args=()
    if [[ -n "$iqe_env_vars" ]]; then
        IFS=',' read -ra iqe_env_var_pairs <<< "$iqe_env_vars"
        for pair in "${iqe_env_var_pairs[@]}"; do
            if [[ -n "$pair" ]]; then
                iqe_env_var_args+=(--env-var "$pair")
            fi
        done
    fi

    local ibutsu_source_args=()
    if [[ -n "${IBUTSU_SOURCE:-}" ]]; then
        ibutsu_source_args=(--ibutsu-source "$IBUTSU_SOURCE")
    fi

    export BONFIRE_NS_REQUESTER="$ns_requester"

    pod=$(bonfire deploy-iqe-cji "$component_name" \
        --marker "$iqe_marker_expression" \
        --filter "$iqe_filter_expression" \
        --image-tag "${iqe_image_tag}" \
        --requirements "$iqe_requirements" \
        --requirements-priority "$iqe_requirements_priority" \
        --test-importance "$iqe_test_importance" \
        --plugins "$iqe_plugins" \
        --env "$iqe_env" \
        --cji-name "$cji_name" \
        --parallel-enabled "$iqe_parallel_enabled" \
        $selenium_arg \
        $playwright_arg \
        "${ibutsu_source_args[@]}" \
        "${iqe_env_var_args[@]}" \
        --namespace "$ns")

    container=$(oc_wrapper get pod "$pod" -n "$ns" -o jsonpath="{.status.containerStatuses[0].name}")
    oc_wrapper logs -n "$ns" "$pod" -c "$container" -f &
    pid=$!
    trap "cleanup $pid" EXIT

    oc_wrapper wait "--timeout=$iqe_cji_timeout" --for=condition=JobInvocationComplete -n "$ns" "cji/$cji_name"
    oc_wrapper get -o json -n "$ns" "cji/$cji_name" | check_cji_jobs.py
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
