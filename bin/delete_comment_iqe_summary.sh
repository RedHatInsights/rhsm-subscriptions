# Post comment about the IQE summary.
set -e

if [[ -n $ghprbPullId ]]; then
  # delete existing comments for the same user
  user="InsightsDroid"

  # set +e so that if this POST fails, the entire run will not fail
  set +e

  COMMENTS=$(curl -s \
    -H "Accept: application/vnd.github.v3+json" \
    -H "Authorization: token ${GITHUB_TOKEN}" \
    ${GITHUB_API_URL}/repos/${ghprbGhRepository}/issues/${ghprbPullId}/comments)

  echo "$COMMENTS" | jq -c '.[] | select(.user.login == "'$user'") | .id' | while read -r COMMENT_ID; do
    COMMENT_ID=$(echo $COMMENT_ID | tr -d '"')
    curl -s \
        -X DELETE \
        -H "Authorization: token ${GITHUB_TOKEN}" \
        ${GITHUB_API_URL}/repos/${ghprbGhRepository}/issues/comments/${COMMENT_ID}
  done

  set -e
fi