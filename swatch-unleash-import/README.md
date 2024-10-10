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

# Testing with Bonfire Locally

1. Build the images
```
./bin/build-images.sh -p swatch-unleash-import
```

2. Deploy via bonfire (REPLACE **$YOUR_IMAGE_TAG** WITH THE ONE FROM YOUR BUILD)
```
bonfire deploy rhsm  --timeout=1800  --optional-deps-method none  \
--frontends false  --no-remove-resources app:rhsm \
-C rhsm -p rhsm/SWATCH_UNLEASH_IMPORT_IMAGE=quay.io/mstead/swatch-unleash-import \
-p rhsm/SWATCH_UNLEASH_IMPORT_IMAGE_TAG=$YOUR_IMAGE_TAG
```

3. Use port forwarding to get access to the Unleash console.
```
oc port-forward pods/$(oc get pods | cut -d' ' -f1 | grep 'env.*featureflags') 4243:4242
```

4. Access unleash UI to ensure that the **swatch.swatch-metrics-hbi.emit-events** flag was created.
```
# user/pass: admin / unleash4all
http://localhost:4243
```