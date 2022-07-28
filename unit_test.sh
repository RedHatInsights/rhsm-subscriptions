# NOTE: this is just a stub from https://github.com/RedHatInsights/bonfire/blob/64e50e1b5536d1eb8d874ea62caf0a572b5e32f1/cicd/examples/unit_test_example.sh#L22-L29
# If your unit tests store junit xml results, you should store them in a file matching format `artifacts/junit-*.xml`
# If you have no junit file, use the below code to create a 'placeholder' result file so Jenkins will not fail
mkdir -p $ARTIFACTS_DIR
cat << EOF > $ARTIFACTS_DIR/junit-placeholder.xml
<testsuite tests="1">
    <testcase classname="placeholder" name="placeholdertest"/>
</testsuite>
EOF
