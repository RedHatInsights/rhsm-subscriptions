# Docker Compose
Docker Compose relies on Docker Engine for any meaningful work, so make sure you have Docker Engine installed either locally or remote, depending on your setup.

##Installation

On Mac and Windows you can just install [Docker Desktop](https://docs.docker.com/desktop/), Docker Compose is included as part of those desktop installs.

On Linux systems, first install the [Docker Engine](https://docs.docker.com/engine/install/#server) for your OS as described on the [Get Docker](https://docs.docker.com/get-docker/) page, then come back here for instructions on installing Compose on Linux systems.

To run Compose as a non-root user, see [Manage Docker as a non-root user.](https://docs.docker.com/engine/install/linux-postinstall/)

## Usage
Docker Desktop provides an interface to manage and use CLI within the provided dashboard. Once you startup the dependencies.

```
1. cd docker

2. docker-compose up 
```
*NOTE*: Docker-compose file & container-compose cannot be in the same folder, as it cause a duplicate mount with podman-compose. Because of the init_db.sh script.

To terminate the running images:
```
docker-compose down
```

*NOTE*: If you're on Windows or Mac. You can stop images from the docker desktop.