# build image & push to quay

```bash
QUAY_USER=lburnett
QUAY_TOKEN=Passw0rd
IMAGE=quay.io/lburnett/rhsm
IMAGE_TAG=service_demo

docker login -u="$QUAY_USER" -p="$QUAY_TOKEN" quay.io

docker -build --no-cache -t "${IMAGE}:${IMAGE_TAG}" .
docker -push "${IMAGE}:${IMAGE_TAG}"
```

# login to openshift
`oc login --token=Passw0rd --server=https://api.c-rh-c-eph.8p0c.p1.openshiftapps.com:6443`


# setup Virtual Environment
```bash
VENV_DIR=~/bonfire_venv
mkdir -p $VENV_DIR
python3 -m venv $VENV_DIR
. $VENV_DIR/bin/activate
pip install crc-bonfire
pip install --upgrade crc-bonfire
```

# configure ~/.config/bonfire/config.yaml
```yaml
appsFile:
  host: gitlab
  repo: insights-platform/cicd-common
  path: bonfire_configs/ephemeral_apps.yaml

apps:
- name: lburnett-service-demo # BONFIRE_APP_NAME
  components:
    - name: service_demo # BONFIRE_COMPONENT_NAME
      host: local
      repo: /home/lburnett/code/rhsm-subscriptions/demo
      path: /deploy/clowdapp.yaml
```

# deploy container with bonfire
```bash
IMAGE=quay.io/lburnett/rhsm
IMAGE_TAG=service_demo
BONFIRE_APP_NAME=lburnett-service-demo
BONFIRE_COMPONENT_NAME=service_demo
NAMESPACE=$(bonfire namespace reserve)

echo $NAMESPACE

bonfire deploy -n $NAMESPACE \
--no-remove-resources $BONFIRE_COMPONENT_NAME \
-p $BONFIRE_COMPONENT_NAME/IMAGE=$IMAGE \
-i $IMAGE=$IMAGE_TAG $BONFIRE_APP_NAME

```

# port-forward the deployed container
note: i have the namespace hardcoded here...replace with the namespace you reserved
`oc port-forward $(oc -n ephemeral-itjulc get -o name pod | grep fruit) 8000`


# test rest endpoint
note: example uses httpie
```bash
➜ demo (lburnett/sample_service) ✗ http :8000/api/rhsm-subscriptions/v1/fruits
HTTP/1.1 200
Connection: keep-alive
Content-Type: application/json
Date: Wed, 02 Feb 2022 22:36:00 GMT
Keep-Alive: timeout=60
Transfer-Encoding: chunked

{
    "data": [
        {
            "color": "yellow",
            "id": "14b5f7a5-3a04-43c6-ac37-b856bba69492",
            "name": "banana"
        }
    ]
}

```
