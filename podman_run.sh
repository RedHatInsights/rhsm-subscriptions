if command -v podman && [[ -z "$USE_DOCKER" ]]; then
  TEMPSOCKET=$(mktemp)
  export SOCKET=$TEMPSOCKET
  export PODMAN_USERNS=keep-id
  version=$(podman --version)
  # if podman is older than podman 2.x, then the service is not available
  if [[ ! "$version" == "podman version 1."* ]]; then
    export CONTAINER_HOST=unix://$SOCKET
    echo Running podman service at $CONTAINER_HOST
    podman system service --time 3600 $CONTAINER_HOST&
    export PODMAN_PID=$!
  fi
  echo Running command '`'$@'`' via podman
  export podman_cmd=podman
else
  export SOCKET=/var/run/docker.sock
  export CONTAINER_HOST=unix://$SOCKET
  podman_cmd=docker
  echo Running command '`'$@'`' via docker
fi
export DOCKER_HOST=$CONTAINER_HOST
# needs host network and selinux labeling disabled to support testcontainers
$podman_cmd run \
  --net=host \
  --security-opt label=disable \
  --rm \
  --user=$UID \
  -e DOCKER_HOST=$CONTAINER_HOST \
  -e GRADLE_USER_HOME=/workspace/.gradle \
  -w /workspace \
  -v $SOCKET:$SOCKET:Z \
  -v $(pwd):/workspace:Z \
  registry.access.redhat.com/ubi9/openjdk-17-runtime \
  "$@"
  RETURN_CODE=$?
if [[ -n "$PODMAN_PID" ]]; then
  echo Stopping podman service at $CONTAINER_HOST
  kill $PODMAN_PID
fi
if [[ -n "$TEMPSOCKET" ]]; then
  rm -f $TEMPSOCKET
fi
exit $RETURN_CODE
