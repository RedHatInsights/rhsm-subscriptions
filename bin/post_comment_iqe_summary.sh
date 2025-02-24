# Post comment about the IQE summary.
set -e

: "${IQE_IMAGE_TAG:='""'}"

if [[ -n $ghprbPullId ]]; then
  iqe_pod=$(oc_wrapper get pods -n $NAMESPACE | grep iqe | cut -d' ' -f1)
  logs=$(oc_wrapper logs -n $NAMESPACE $iqe_pod)

  # Summary
  summary=$(echo "$logs" | grep "warnings in " | grep "skipped" | sed -r 's/=//g')

  # The test execution might fail for multiple reasons:
  if [[ $logs == *"Build timed out"*  ]]; then
    ## 1. the build timeout
    result="Result: Build Timeout"
  elif [[ $logs == *"FAILED tests"*  ]]; then
    ## 2. test failures
    result="Result: Failed"
    failed_tests=$(echo -e '|Failures|\n|:-|\n')
    for t in $(echo "$logs" | grep -oP '(?<=FAILED )tests/integration/[^ ]+' | sed 's|tests/||'); do
      failed_tests=$(echo -e "$failed_tests \n|$t|")
    done
  else
    result="Result: Success"
  fi

  body=$(cat <<EOF
### IQE Tests Summary Report
${summary}
${result}
IQE plugin image: https://quay.io/cloudservices/iqe-tests:${IQE_IMAGE_TAG}
${failed_tests}
EOF
  )

  # Remove end of lines
  body=$(echo "$body" | sed ':a;N;$!ba;s/\n/\\r\\n/g')

  # set +e so that if this POST fails, the entire run will not fail
  set +e

  curl -X POST \
    -H "Accept: application/vnd.github.v3+json" \
    -H "Authorization: token ${GITHUB_TOKEN}" \
    -H "Content-Type: application/json; charset=utf-8" \
    ${GITHUB_API_URL}/repos/${ghprbGhRepository}/issues/${ghprbPullId}/comments \
    -d "{\"body\": \"$body\"}"

  set -e
fi