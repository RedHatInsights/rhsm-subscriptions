# swatch-unleash-import

Defines an image that can be run to create/update unleash feature
flags in an ephemeral environment.

## NOTES
**./Dockerfile**
This file is used when building the image that runs the `bin/import-features.sh` script that
loads all the feature flags into the target environment.

**deploy/rhsm-clowdapp.yaml**
This file defines the Job/ConfigMap that will be run to update the feature flags on deployment.
This is only ever done for the epehemeral environment (see app-interface).

**.unleash/flags.json**
This defines the flags that are created in our containers when deploying our
service dependancies via docker/podman (docker-compose).

