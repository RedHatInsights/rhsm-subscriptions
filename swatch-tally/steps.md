podman-compose down
podman-compose up -d


./mvnw install -DskipTests
./mvnw -f swatch-database/pom.xml exec:java




SERVICE_INSTANCE_INGRESS_KAFKA_MAX_POLL_RECORDS=500 MANAGEMENT_SERVER_PORT=9002 SERVER_PORT=8002 DEV_MODE=true ./mvnw -pl swatch-tally spring-boot:run







curl -X POST http://localhost:9080/topics/platform.rhsm-subscriptions.service-instance-ingress -H "Content-Type: application/vnd.kafka.json.v2+json" -d '{"records":[{"key":"i-0d83c7a09e2589d87","value":{"event_source":"rhelemeter","event_type":"snapshot_rhel-for-x86-els-payg_vcpus","org_id":"13259775","instance_id":"i-0d83c7a09e2589d87","metering_batch_id":"783bbf77-e65d-4d3a-99bc-ac48dc179c2f","service_type":"RHEL System","timestamp":"2025-07-03T19:00:00Z","expiration":"2025-07-03T20:00:00Z","display_name":"ip-10-31-97-115.us-east-1.aws.redhat.com","measurements":[{"value":1.0,"metric_id":"vCPUs"}],"product_ids":["69","204"],"sla":"Premium","usage":"Production","billing_provider":"aws","billing_account_id":"746157280291","product_tag":["rhel-for-x86-els-payg"],"conversion":true,"isHypervisor":false,"isVirtual":false,"isUnmappedGuest":false}}]}'




psql -U postgres -d rhsm-subscriptions -c "SELECT data->>'instance_id' as instance_id, data->>'timestamp' as timestamp, data->'measurements' as measurements FROM events;"



curl -s "http://localhost:8003/api/rhsm-subscriptions/v1/internal/rpc/tally/events" -H "Content-Type: application/json" -H "x-rh-swatch-psk: placeholder" -d '[{"org_id": "13259775", "instance_id": "test-fix-verification", "timestamp": "2025-07-16T00:00:00Z", "event_type": "snapshot_rhel-for-x86-els-payg_cores", "event_source": "rhelemeter", "measurements": [{"metric_id": "Cores", "value": 2.0}], "product_tag": ["rhel-for-x86-els-payg"], "service_type": "RHEL System", "display_name": "test-instance", "sla": "Premium", "usage": "Production", "billing_provider": "aws", "billing_account_id": "test-account-123"}]'








curl -X POST http://localhost:9080/topics/platform.rhsm-subscriptions.service-instance-ingress -H "Content-Type: application/vnd.kafka.json.v2+json" -d '{"records":[{"key":"i-0d83c7a09e2589d87","value":{"event_source":"rhelemeter","event_type":"snapshot_rhel-for-x86-els-payg_vcpus","org_id":"13259775","instance_id":"i-0d83c7a09e2589d87","metering_batch_id":"783bbf77-e65d-4d3a-99bc-ac48dc179c2f","service_type":"RHEL System","timestamp":"2025-07-03T19:00:00Z","expiration":"2025-07-03T20:00:00Z","display_name":"ip-10-31-97-115.us-east-1.aws.redhat.com","measurements":[{"value":1.0,"metric_id":"vCPUs"}],"product_ids":["69","204"],"sla":"Premium","usage":"Production","billing_provider":"aws","billing_account_id":"746157280291","product_tag":["rhel-for-x86-els-payg"],"conversion":true,"isHypervisor":false,"isVirtual":false,"isUnmappedGuest":false}}]}'