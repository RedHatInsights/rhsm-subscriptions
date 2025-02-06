# Post comment about the IQE summary.
set -e

: "${IQE_IMAGE_TAG:='""'}"

if [[ -n $ghprbPullId ]]; then

  body=$(cat <<EOF
### IQE Tests Summary Report
- tests image: https://quay.io/cloudservices/iqe-tests:${IQE_IMAGE_TAG}
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