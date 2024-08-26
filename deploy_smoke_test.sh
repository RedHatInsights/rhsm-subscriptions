# Run smoke tests as a ClowdJobInvocation deployed by bonfire

# Env vars defined by caller:
#COMPONENT_NAME -- name of ClowdApp to run tests against /  app-sre "resourceTemplate"
#IQE_CJI_TIMEOUT="10m" -- timeout value to pass to 'oc wait', should be slightly higher than expected test run time
#IQE_MARKER_EXPRESSION="something AND something_else" -- pytest marker, can be "" if no filter desired
#IQE_FILTER_EXPRESSION="something AND something_else" -- pytest filter, can be "" if no filter desired
#IQE_IMAGE_TAG="something" -- image tag to use for IQE pod, leave unset to use ClowdApp's iqePlugin value
#IQE_REQUIREMENTS="something,something_else" -- iqe requirements filter, can be "" if no filter desired
#IQE_REQUIREMENTS_PRIORITY="something,something_else" -- iqe requirements filter, can be "" if no filter desired
#IQE_TEST_IMPORTANCE="something,something_else" -- iqe test importance filter, can be "" if no filter desired
#IQE_PLUGINS="plugin1,plugin2" -- IQE plugins to run tests for, leave unset to use ClowdApp's iqePlugin value
#IQE_ENV="something" -- value to set for ENV_FOR_DYNACONF, default is "clowder_smoke"
#IQE_SELENIUM="true" -- whether to run IQE pod with a selenium container, default is "false"
#IQE_RP_ARGS=True -- Turn on reporting to reportportal
#IQE_IBUTSU_SOURCE="post_stage" -- update the ibutsu source for the current run
#IQE_ENV_VARS="ENV_VAR1=value1,ENV_VAR2=value2" -- custom set of extra environment variables to set on IQE pod
#NAMESPACE="mynamespace" -- namespace to deploy iqe pod into, usually already set by 'deploy_ephemeral_env.sh'

# Env vars set by 'bootstrap.sh':
#ARTIFACTS_DIR -- directory where test run artifacts are stored

# In order for the deploy-iqe-cji to run correctly, we must set the marker and filter to "" if they
# are not already set by caller
# https://unix.stackexchange.com/questions/122845/using-a-b-for-variable-assignment-in-scripts/122848#122848
set -e

: "${IQE_MARKER_EXPRESSION:='""'}"
: "${IQE_FILTER_EXPRESSION:='""'}"
: "${IQE_IMAGE_TAG:='""'}"
: "${IQE_REQUIREMENTS:='""'}"
: "${IQE_REQUIREMENTS_PRIORITY:='""'}"
: "${IQE_TEST_IMPORTANCE:='""'}"
: "${IQE_PLUGINS:='""'}"
: "${IQE_ENV:=clowder_smoke}"
: "${IQE_SELENIUM:=false}"
: "${IQE_PARALLEL_ENABLED:='""'}"
: "${IQE_PARALLEL_WORKER_COUNT:='""'}"
: "${IQE_RP_ARGS:='""'}"
: "${IQE_IBUTSU_SOURCE:='""'}"
: "${IQE_ENV_VARS:=}"

_running_in_rhel7() {
    grep -q "Red Hat Enterprise Linux.*7\." '/etc/redhat-release'
}

# REMOVE after https://issues.redhat.com/browse/APPSRE-7886
if _running_in_rhel7; then
    MC_IMAGE="quay.io/cloudservices/mc:RELEASE.2023-05-30T22-41-38Z"
else
    MC_IMAGE="quay.io/cloudservices/mc:latest"
fi

# minio client is used to fetch test artifacts from minio in the ephemeral ns
echo "Running: docker pull ${MC_IMAGE}"
docker pull ${MC_IMAGE}

CJI_NAME="$COMPONENT_NAME"

if [[ -z $IQE_CJI_TIMEOUT ]]; then
    echo "Error: no timeout set; export IQE_CJI_TIMEOUT before invoking cji_smoke_test.sh"
    exit 1
fi

SELENIUM_ARG=""
if [ "$IQE_SELENIUM" = "true" ]; then
    SELENIUM_ARG=" --selenium "
fi

ENV_VAR_ARGS=""
if [ ! -z "$IQE_ENV_VARS" ]; then
    IFS=',' read -ra values_array <<< "$IQE_ENV_VARS"
    for i in "${values_array[@]}"; do
        ENV_VAR_ARGS="${ENV_VAR_ARGS} --env-var $i"
    done
fi

# check if there is a iqe-tests container image tag with the corresponding PR
# number if not use the default IQE_IMAGE_TAG tag which you have to set in
# order for this to work properly
if [ ! -z "$ghprbPullId" ]; then
    export IQE_IMAGE_TAG_TMP="${IQE_IMAGE_TAG}-pr-${ghprbPullId}"
fi

if [ ! -z "$gitlabMergeRequestIid" ]; then
    export IQE_IMAGE_TAG_TMP="${IQE_IMAGE_TAG}-pr-${gitlabMergeRequestIid}"
fi

if [ ! -z "$IQE_IMAGE_TAG_TMP" ]; then
    set +e
    if docker search quay.io/cloudservices/iqe-tests --list-tags --limit 5000 | grep -e "$IQE_IMAGE_TAG_TMP"; then
        echo "Found IQE_IMAGE_TAG=$IQE_IMAGE_TAG_TMP. It will be used for testing."
        export IQE_IMAGE_TAG="$IQE_IMAGE_TAG_TMP"
    fi
    set -e
fi

# Invoke the CJI using the options set via env vars
set -x
POD=$(
    bonfire deploy-iqe-cji $COMPONENT_NAME \
    --marker "$IQE_MARKER_EXPRESSION" \
    --filter "$IQE_FILTER_EXPRESSION" \
    --image-tag "${IQE_IMAGE_TAG}" \
    --requirements "$IQE_REQUIREMENTS" \
    --requirements-priority "$IQE_REQUIREMENTS_PRIORITY" \
    --test-importance "$IQE_TEST_IMPORTANCE" \
    --plugins "$IQE_PLUGINS" \
    --env "$IQE_ENV" \
    --cji-name $CJI_NAME \
    $SELENIUM_ARG \
    --parallel-enabled $IQE_PARALLEL_ENABLED \
    --parallel-worker-count $IQE_PARALLEL_WORKER_COUNT \
    --rp-args $IQE_RP_ARGS \
    --ibutsu-source $IQE_IBUTSU_SOURCE \
    $ENV_VAR_ARGS \
    --namespace $NAMESPACE)
set +x

# Pipe logs to background to keep them rolling in jenkins
CONTAINER=$(oc_wrapper get pod $POD -n $NAMESPACE -o jsonpath="{.status.containerStatuses[0].name}")
oc_wrapper logs -n $NAMESPACE $POD -c $CONTAINER -f &

# Wait for the job to Complete or Fail before we try to grab artifacts
# condition=complete does trigger when the job fails
set -x
oc_wrapper wait --timeout=$IQE_CJI_TIMEOUT --for=condition=JobInvocationComplete -n $NAMESPACE cji/$CJI_NAME
set +x

# Set up port-forward for minio
set -x
LOCAL_SVC_PORT=$(python -c 'import socket; s=socket.socket(); s.bind(("", 0)); print(s.getsockname()[1]); s.close()')
oc_wrapper port-forward svc/env-$NAMESPACE-minio $LOCAL_SVC_PORT:9000 -n $NAMESPACE &
set +x
sleep 5
PORT_FORWARD_PID=$!

# Get the secret from the env
set -x
oc_wrapper get secret env-$NAMESPACE-minio -o json -n $NAMESPACE | jq -r '.data' > minio-creds.json
set +x

# Grab the needed creds from the secret
export MINIO_ACCESS=$(jq -r .accessKey < minio-creds.json | base64 -d)
export MINIO_SECRET_KEY=$(jq -r .secretKey < minio-creds.json | base64 -d)
export MINIO_HOST=localhost
export MINIO_PORT=$LOCAL_SVC_PORT

if [ -z "$MINIO_ACCESS" ] || [ -z "$MINIO_SECRET_KEY" ] || [ -z "$MINIO_PORT" ]; then
    echo "Failed to fetch minio connection info when running 'oc' commands"
    exit 1
fi

# Setup the minio client to auth to the local eph minio in the ns
echo "Fetching artifacts from minio..."

CONTAINER_NAME="mc-${JOB_NAME}-${BUILD_NUMBER}"
BUCKET_NAME="${POD}-artifacts"
CMD="mkdir -p /artifacts &&
mc --no-color --quiet alias set minio http://${MINIO_HOST}:${MINIO_PORT} ${MINIO_ACCESS} ${MINIO_SECRET_KEY} &&
mc --no-color --quiet mirror --overwrite minio/${BUCKET_NAME} /artifacts/
"

run_mc () {
    echo "running: docker run -t --net=host --name=$CONTAINER_NAME --entrypoint=\"/bin/sh\" $MC_IMAGE -c \"$CMD\""
    set +e
    docker run -t --net=host --name=$CONTAINER_NAME --entrypoint="/bin/sh" $MC_IMAGE -c "$CMD"
    RET_CODE=$?
    docker cp $CONTAINER_NAME:/artifacts/. $ARTIFACTS_DIR
    docker rm $CONTAINER_NAME
    set -e
    return $RET_CODE
}

# Add retry logic for intermittent minio connection failures
MINIO_SUCCESS=false
for i in $(seq 1 5); do
    if run_mc; then
        MINIO_SUCCESS=true
        break
    else
        if [ "$i" -lt "5" ]; then
            echo "WARNING: minio artifact copy failed, retrying in 5sec..."
            sleep 5
        fi
    fi
done

if [ "$MINIO_SUCCESS" = false ]; then
    echo "ERROR: minio artifact copy failed"
    exit 1
fi

echo "checking if files exist"

for PLUGIN in ${IQE_PLUGINS//,/ }; do
    PLUGIN_NAME=${PLUGIN//-/_}
    JUNIT_SEQUENTIAL_OUTPUTS=(
        "iqe-${PLUGIN_NAME}-sequential.log"
        "junit-${PLUGIN_NAME}-sequential.xml"
    )

    for file in "${JUNIT_SEQUENTIAL_OUTPUTS[@]}"; do
        if [ ! -e "$ARTIFACTS_DIR/$file" ]; then
            echo "The file $file does not exist. CJI Test(s) may have failed."
            exit 1
        fi
    done

    if [ "$IQE_PARALLEL_ENABLED" = "true" ]; then
        JUNIT_PARALLEL_OUTPUTS=(
            "iqe-${PLUGIN_NAME}-parallel.log"
            "junit-${PLUGIN_NAME}-parallel.xml"
        )

        for file in "${JUNIT_PARALLEL_OUTPUTS[@]}"; do
            if [ ! -e "$ARTIFACTS_DIR/$file" ]; then
                echo "The file $file does not exist. CJI Test(s) may have failed."
                exit 1
            fi
        done
    fi
done

echo "copied artifacts from iqe pod: "
ls -l $ARTIFACTS_DIR
