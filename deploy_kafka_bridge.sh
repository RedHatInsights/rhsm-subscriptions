#!/bin/bash
NAMESPACE=${NAMESPACE:-$(oc project -q)}
KAFKA_BOOTSTRAP_SERVERS=$(oc get -n $NAMESPACE kafka -o go-template --template='{{ range .items }}{{ range .status.listeners }}{{ .bootstrapServers }}{{ end }}{{ end }}')
oc process --local=true -f kafka-bridge.yaml -p KAFKA_BOOTSTRAP_SERVERS=$KAFKA_BOOTSTRAP_SERVERS | oc apply -n $NAMESPACE -f -
echo "In the EE, the kafka-bridge URL is http://kafka-bridge-bridge-service:8080"
echo "To port-forward, use:"
echo "  oc port-forward svc/kafka-bridge-bridge-service 8080:8080"
