
## MacOS specific configuration and troubleshooting

### HOST_NAME Environment Variable Issue

When running Quarkus services on macOS (e.g., `make swatch-contracts`), you may encounter:
```
Failed to load config value of type class java.lang.String for: HOST_NAME
```

**Solution**: Set the HOSTNAME environment variable before running services:
```bash
export HOSTNAME=$(hostname)
make swatch-contracts
```
And ideally add the export to `~/.zshrc`(`~/.bashrc`).

**Root Cause**: Unlike Linux distributions (Fedora, RHEL), macOS doesn't automatically export the `HOSTNAME` environment variable, which is required by the Quarkus dev profile configuration. MacOS automatically exports `HOST` environment variable.

### Troubleshooting on Apple Silicon

#### 1. CPU Architecture Mismatch

**Problem:**
When running the compose file on an Apple Silicon (ARM64) Mac, services fail to start if their images are built for the Intel x86 (`linux/amd64`) architecture. This results in an `exec format error`.

**Solution:**
To run x86 images, you must instruct Podman to use a compatibility layer within its Linux VM. On Apple Silicon, there are two options:

- **Rosetta 2 (Default):** Apple's high-performance translation layer for running x86_64 Linux binaries. 
    It is **significantly faster** than QEMU and is the preferred method. You must ensure Rosetta is enabled for your Podman machine and it is installed on your machine.
    ```bash
    softwareupdate --install-rosetta --agree-to-license
    ```
    To verify it is enabled in Podman Desktop, follow the official documentation [Native Apple Rosetta translation layer](https://podman-desktop.io/docs/podman/rosetta).

- **QEMU (Not recommended):** A general-purpose emulator. Not recommended as it comes with a **significant performance penalty**.

**Command:**
Regardless of the underlying translator, you still use the `--platform` flag to tell Podman to run the `amd64` variant of an image.

```bash
podman-compose --podman-pull-args "--platform linux/amd64" --podman-run-args "--platform linux/amd64" --podman-build-args "--platform linux/amd64" up -d
```

#### 2. Building Container Images with `build-images.sh`

**Problem:**
When running `bin/build-images.sh` on Apple Silicon, the built images default to the `linux/arm64` architecture. These images will fail with `exec format error` when deployed to `linux/amd64` environments (e.g., ephemeral clusters, OpenShift).

**Solution:**
The script **auto-detects Apple Silicon** and automatically adds `--platform linux/amd64` to the `podman build` commands. No extra flags are needed:
```bash
bin/build-images.sh swatch-contracts
```

You can also override the platform explicitly with `-p`:
```bash
bin/build-images.sh -p linux/amd64 swatch-contracts
```

**Prerequisites:** Make sure Rosetta 2 is installed and enabled in Podman (see section 1 above).

#### 3. Port Binding Conflict ("Address Already in Use")

**Problem:**
A service may fail to start with an error like `listen tcp4 ...: bind: address already in use`, even when commands like `sudo lsof` on the macOS host show the port is free.

**Root Cause:**
This issue occurs inside the Podman Linux VM, not directly on the macOS host. The problem arises when a service in the compose file attempts to bind the same port to both the IPv4 localhost (`127.0.0.1`) and the IPv6 localhost (`[::1]`).

The Linux kernel's default setting (`net.ipv6.bindv6only = 0`) allows a single IPv6 socket to handle both IPv6 and IPv4 traffic. This makes the separate IPv4 binding request redundant and causes a conflict.

**Solution:**
To resolve this, you only need one entry in the `ports` section. The recommended approach is to **use only the IPv6 localhost binding**, as it will cover both protocols.
**This problem should not appear anymore**, since the IPv6 port specifications were already removed from the docker-compose.yml file, leaving this for future reference.

