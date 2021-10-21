#!/bin/bash
# This script does all the proxy magic we need to access hawtio on a cluster
NGINX_PORT=${NGINX_PORT:-8085}
FORWARD_PORT=${FORWARD_PORT:-9095}
pod=$1
if [ $# -lt 1 ]; then
  echo "Usage: $0 <pod>"
  exit 1
fi
if [ -z "$(which jq)" ]; then
  echo "jq not found (hint: sudo dnf install -y jq)"
  exit 1
fi
nginx_config=$(mktemp)
hawtio_base_path=/actuator/hawtio
pod_json=$(oc get pod/$pod -o json)
hawtio_path_override="$(echo "$pod_json" | jq -r '.spec.containers[].env[] | select(.name=="HAWTIO_BASE_PATH") | .value')"
if [ -n "$hawtio_path_override" ]; then
  hawtio_base_path="$hawtio_path_override"
fi
container_port=8080
web_port=$(echo "$pod_json" | jq -r '.spec.containers[].ports[] | select(.name=="web") | .containerPort')
if [ -n "$web_port" ]; then
  container_port=$web_port
fi
oc port-forward pod/$pod $FORWARD_PORT:$container_port&
port_forward_pid=$!
auth = $(echo -n '{"identity":{"associate":{"email":"test@example.com"},"auth_type":"saml-auth","type":"Associate"}}' | base64 -w0)
cat <<EOF > $nginx_config
events {}

http {
    server {
        listen       $NGINX_PORT;

        location $hawtio_base_path {
            proxy_pass http://localhost:$FORWARD_PORT/actuator/hawtio;
            proxy_set_header origin "example.cloud.redhat.com";
            proxy_set_header x-rh-identity "$auth";
        }

        location /actuator/jolokia {
            proxy_pass http://localhost:$FORWARD_PORT/actuator/jolokia;
            proxy_set_header origin "example.cloud.redhat.com";
            proxy_set_header x-rh-identity "$auth";
        }
    }
}
EOF
echo "Proxying pod $pod port $container_port at path $hawtio_base_path so that it can be reached via http://localhost:$NGINX_PORT$hawtio_base_path"
podman run -p 8085:80 --network=host --rm --name hawtio-proxy -v $nginx_config:/etc/nginx/nginx.conf:ro,z docker.io/nginx
kill $port_forward_pid
rm -f "$nginx_config"
