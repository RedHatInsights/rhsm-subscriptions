podman run --user=$UID -e GRADLE_HOME=/workspace/.gradle -w /workspace -v $(pwd):/workspace:Z registry.access.redhat.com/ubi8/openjdk-8 "$@"
