SOCKET=$(mktemp)
export CONTAINER_HOST=unix://$SOCKET
export DOCKER_HOST=$CONTAINER_HOST
echo Running podman service at $CONTAINER_HOST
podman system service -t 360 $CONTAINER_HOST&
PODMAN_PID=$!
echo Running command '`'$@'`' via podman
# needs host network and selinux labeling disabled to support testcontainers
podman run \
  --net=host \
  --security-opt label=disable \
  --rm \
  --user=root \
  -e DOCKER_HOST=$CONTAINER_HOST \
  -e GRADLE_USER_HOME=/workspace/.gradle \
  -w /workspace \
  -v $SOCKET:$SOCKET:Z \
  -v $(pwd):/workspace:Z \
  registry.access.redhat.com/ubi8/openjdk-11 \
  "$@"
RETURN_CODE=$?
echo Stopping podman service at $CONTAINER_HOST
kill $PODMAN_PID
rm -f $SOCKET
exit $RETURN_CODE
