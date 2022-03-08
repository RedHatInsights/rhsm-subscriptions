if command -v podman; then
  export SOCKET=$(mktemp)
  export CONTAINER_HOST=unix://$SOCKET
  export PODMAN_USERNS=keep-id
  echo Running podman service at $CONTAINER_HOST
  podman system service -t 360 $CONTAINER_HOST&
  export PODMAN_PID=$!
  echo Running command '`'$@'`' via podman
  export podman_cmd=podman
else
  export SOCKET=/var/run/docker.sock
  export CONTAINER_HOST=unix://$SOCKET
  podman_cmd=docker
  echo Podman not available. Running command '`'$@'`' via docker
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
  registry.access.redhat.com/ubi8/openjdk-11 \
  "$@"
  RETURN_CODE=$?
if [[ -n "$PODMAN_PID" ]]; then
  echo Stopping podman service at $CONTAINER_HOST
  kill $PODMAN_PID
  rm -f $SOCKET
fi
exit $RETURN_CODE
