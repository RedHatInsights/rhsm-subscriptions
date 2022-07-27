#!/bin/bash

set -x

LABELS_DIR="$WORKSPACE/github_labels"
ARTIFACTS_DIR="artifacts"

mkdir -p $LABELS_DIR
mkdir -p $ARTIFACTS_DIR

echo $WORKSPACE

curl -s -H "Accept: application/vnd.github.v3+json" -H "Authorization: token $GITHUB_TOKEN" https://api.github.com/search/issues\?q\=sha:$ghprbActualCommit | jq '.items[].labels[].name' > $LABELS_DIR/github_labels.txt


function check_for_labels() {
    if [ -f $LABELS_DIR/github_labels.txt ]; then
        egrep "$1" $LABELS_DIR/github_labels.txt &>/dev/null
    fi
}

if check_for_labels "bananas-test"; then
  echo "matching bananas"
fi


cp $LABELS_DIR/github_labels.txt $ARTIFACTS_DIR/github_labels.txt

cat << EOF > artifacts/junit-dummy.xml
<testsuite tests="1">
    <testcase classname="dummy" name="dummytest"/>
</testsuite>
EOF