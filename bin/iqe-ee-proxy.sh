#!/bin/bash
TMP_DIR=$(mktemp -d)
echo "    {
      auto_https off
      debug
    }
    " >> ${TMP_DIR}/Caddyfile

NAMESPACE=$(oc project -q)

cleanup() {
  sudo sed -i "/#===== NAMESPACE $NAMESPACE =====/,/#===== NAMESPACE $NAMESPACE =====/d" /etc/hosts
  cat $(dirname $0)/caddy.yaml | oc delete -f -
  rm -rf ${TMP_DIR}
  oc delete configmap/caddyfile
}

trap cleanup TERM SIGINT

if ! grep $NAMESPACE /etc/hosts -q; then
  echo "#===== NAMESPACE $NAMESPACE =====" | sudo tee -a /etc/hosts
  for line in $(oc get svc -o go-template='{{range .items}}{{.metadata.name}}{{";"}}{{range .spec.ports}}{{if .port}}{{.port}}{{";"}}{{end}}{{end}}{{" "}}{{end}}') ; do
    service=$(echo $line | awk -F ";" '{ print $1 }')
    hosts_line="127.0.0.1 $service $service.$NAMESPACE.svc"
    echo "$hosts_line" | sudo tee -a /etc/hosts

    ports=$(echo $line | cut -d ";" -f2- | tr ";" " ")
    for port in $ports; do
      echo "
      http://$service:$port {
        reverse_proxy {
          dynamic a $service $port
        }
      }
      http://$service.$NAMESPACE.svc:$port {
        reverse_proxy {
          dynamic a $service $port
        }
      }
      " >> ${TMP_DIR}/Caddyfile
    done
  done
  podname=$(oc get pod -o name | grep kafka-0 | awk -F "/" '{ print $2 }')
  hostname=$(oc get service -o name | grep kafka-brokers | awk -F "/" '{ print $2 }')
  pod_line="127.0.0.1 $podname.$hostname $podname.$hostname.$NAMESPACE.svc"
  echo "$pod_line" | sudo tee -a /etc/hosts
  echo "#===== NAMESPACE $NAMESPACE =====" | sudo tee -a /etc/hosts
fi

oc port-forward $(oc get pod -o name | grep kafka-0) 9092:9092 &
oc port-forward $(oc get service -o name | grep kafka-bridge-bridge-service) 8080:8080 &
oc port-forward $(oc get service -o name | grep wiremock-service) 8101:8101 &
oc port-forward $(oc get service -o name | grep swatch-mock-prometheus-test-service) 9090:9090 &


oc create configmap caddyfile \
    --from-file=${TMP_DIR}/Caddyfile
cat $(dirname $0)/caddy.yaml | oc apply -f -

oc wait --for=condition=Available deployment/swatch-database-db
oc port-forward deployment/swatch-database-db 5432:5432 &

oc wait --for=condition=Available deployment/caddy
oc port-forward deployment/caddy 9000:9000 8000:8000 5000:5000
