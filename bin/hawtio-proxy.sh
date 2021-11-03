#!/bin/bash
# This script does all the proxy magic we need to access hawtio on a cluster
NGINX_PORT=${NGINX_PORT:-8085}
FORWARD_PORT=${FORWARD_PORT:-9095}

nginx_config=$(mktemp --suffix=.conf --tmpdir nginx-XXXXXX)

function cleanup() {
  rm -f "$nginx_config"

  if [[ -v $port_forward_pid ]] && ps -p "$port_forward_pid" > /dev/null; then
    # The - signifies we should kill all processes in the process group
    kill -- -$port_forward_pid
  fi
}

trap cleanup INT TERM EXIT

pod=$1
if [ $# -lt 1 ]; then
  echo "Usage: $0 <POD NAME>"
  exit 1
fi

if [ -z "$(which jq)" ]; then
  echo "jq not found (hint: sudo dnf install -y jq)"
  exit 1
fi

hawtio_base_path=/actuator/hawtio
pod_json=$(oc get "pod/$pod" -o json)

hawtio_path_override=$(echo "$pod_json" | jq -r '.spec.containers[].env[] | select(.name=="HAWTIO_BASE_PATH") | .value')
if [ -n "$hawtio_path_override" ]; then
  hawtio_base_path=$hawtio_path_override
fi

container_port=8080
web_port=$(echo "$pod_json" | jq -r '.spec.containers[].ports[] | select(.name=="web") | .containerPort')
if [ -n "$web_port" ]; then
  container_port=$web_port
fi


# Put oc into a separate process group by enabling job control temporarily. This will allows us
# to kill and children oc spawns
set -m
# port-forward has the property of notifying every time it forwards. This is annoying and
# verbose for Hawtio where the page makes AJAX requests to the server periodically.
oc port-forward "pod/$pod" "$FORWARD_PORT:$container_port" | grep -v "^Handling connection.*$" &
set +m
port_forward_pid=$!

auth=$(echo -n '{"identity":{"associate":{"email":"test@example.com"},"auth_type":"saml-auth","type":"Associate"}}' | base64 -w0)
cat <<EOF > "$nginx_config"
events {}

http {
    # Map a value to \$loggable - 0 for 2xx and 3xx requests, 1 otherwise
    map \$status \$loggable {
        "~^[23]" 0;
        default 1;
    }
    # Suppress messages that have \$loggable of zero (i.e. 2xx and 3xx requests)
    access_log /var/log/nginx/access.log combined if=\$loggable;
    server {
        listen $NGINX_PORT;

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

green=$(tput setaf 2)
reset=$(tput sgr0)

echo "Proxying pod $pod port $container_port at path $hawtio_base_path"
echo "${green}Go to http://localhost:${NGINX_PORT}${hawtio_base_path}${reset}"

podman run -p $NGINX_PORT:80 --network=host --rm --name hawtio-proxy -e NGINX_ENTRYPOINT_QUIET_LOGS=1 -v $nginx_config:/etc/nginx/nginx.conf:ro,z docker.io/nginx

echo "Bye!"
