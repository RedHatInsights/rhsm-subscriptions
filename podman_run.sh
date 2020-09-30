podman run --rm --user=root -e GRADLE_USER_HOME=/workspace/.gradle -w /workspace -v $(pwd):/workspace:Z registry.access.redhat.com/ubi8/openjdk-11 "$@"
