# Observability playbook

This guide will help us to set up the environment with Kafka, Database and Splunk into local with all our swatch services. 

We'll exercise all the swatch integrations that are part of the diagram in [here](https://miro.com/app/board/uXjVLZZFmEc=/?share_link_id=967248979294).

## Prerequisites

Read more about the prerequisites in [here](../../CONTRIBUTING.md#build).

## Set Up
- Dependant services: instructions [here](../../README.md#dependent-services).
- splunk: instructions [here](../../README.md#splunk).
- Opentelemetry (OTEL) Exporter: instructions [here](../../README.md#opentelemetry-otel-exporter).
- Wiremock (for prometheus): instructions [here](../../README.md#wiremock-service).
- perform database migrations: `./gradlew liquibaseUpdate --no-daemon`
- build all the swatch services: `./gradlew clean build -x test`

## Run SWATCH Services

- **swatch-metrics**: port 8002

```
SERVER_PORT=8002 \
QUARKUS_MANAGEMENT_PORT=9002 \
ENABLE_SPLUNK_HEC=true \
SPLUNK_HEC_URL=https://localhost:8088 \
SPLUNK_HEC_TOKEN=29fe2838-cab6-4d17-a392-37b7b8f41f75 \
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317 \
DISABLE_OTEL=false \
EVENT_SOURCE=telemeter \
PROM_URL="http://localhost:8101/api/v1/" \
./gradlew :swatch-metrics:quarkusDev
```

- **swatch-tally**: port 8003

```
java -DENABLE_SPLUNK_HEC=true \
-DSERVER_PORT=8003 \
-DMANAGEMENT_PORT=9003 \
-DSPLUNK_HEC_URL=https://localhost:8088 \
-DSPLUNK_HEC_TOKEN=29fe2838-cab6-4d17-a392-37b7b8f41f75 \
-DSPLUNK_DISABLE_CERTIFICATE_VALIDATION=true \
-DSPLUNK_SOURCE_TYPE=springboot_server \
-DINVENTORY_DATABASE_SCHEMA=hbi \
-Dspring.profiles.active=worker,api,kafka-queue \
-jar build/libs/rhsm-subscriptions-*.jar
```

- **swatch-billable-usage**: port 8004

```
SERVER_PORT=8004 \
QUARKUS_MANAGEMENT_PORT=9004 \
ENABLE_SPLUNK_HEC=true \
SPLUNK_HEC_URL=https://localhost:8088 \
SPLUNK_HEC_TOKEN=29fe2838-cab6-4d17-a392-37b7b8f41f75 \
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317 \
DISABLE_OTEL=false \
./gradlew :swatch-billable-usage:quarkusDev
```

- **swatch-system-conduit**: port 8005

```
java -DENABLE_SPLUNK_HEC=true \
-DSERVER_PORT=8005 \
-DMANAGEMENT_PORT=8005 \
-DSPLUNK_HEC_URL=https://localhost:8088 \
-DSPLUNK_HEC_TOKEN=29fe2838-cab6-4d17-a392-37b7b8f41f75 \
-DSPLUNK_DISABLE_CERTIFICATE_VALIDATION=true \
-DSPLUNK_SOURCE_TYPE=springboot_server \
-DRHSM_USE_STUB=true \
-DRHSM_BATCH_SIZE=1 \
-jar swatch-system-conduit/build/libs/swatch-system-conduit-*.jar
```

## Scenarios

### Events ingestion by API

Services:
- **swatch-metrics**
- **swatch-tally**

Triggered by:
```
curl -v -H 'Origin: https://service.redhat.com' -X PUT http://localhost:8002/api/swatch-metrics/v1/internal/metering/sync
```

The swatch metrics service produces events that are consumed by the swatch tally. Then, swatch tally will consume 
these events in batches which will not correlate the incoming trace ID from the swatch metrics and will generate a 
new trace ID instead.

### <SnapshotSummaryProducer in Swatch tally> to <TallySummaryMessageConsumer in Swatch Billable Usage>

Services:
- **swatch-tally**
- **swatch-billable-usage**

Steps:

1. Populate some data into the database:
```
INSERT INTO public.events (event_id, timestamp, data, event_type, event_source, instance_id, org_id, metering_batch_id, record_date) 
VALUES ('8c57046a-7071-4fc9-a543-650b868edcc0', '2024-06-21 16:00:00.000000 +00:00', '{"sla": "Premium", "org_id": "12345678", "event_id": "8c57046a-7071-4fc9-a543-650b868edcc0", "timestamp": "2024-06-21T16:00:00Z", "conversion": false, "event_type": "snapshot_rhel-for-x86-els-payg_vcpus", "expiration": "2024-06-21T17:00:00Z", "instance_id": "efe37aa6-ac56-41df-8c8a-c260bd498f5b", "product_ids": ["204", "69"], "product_tag": ["rhel-for-x86-els-payg"], "record_date": "2024-07-24T18:40:04.224170097Z", "display_name": "ip-10-128-200-231.ec2.internal", "event_source": "rhelemeter", "measurements": [{"value": 5.0, "metric_id": "vCPUs"}], "service_type": "RHEL System", "billing_provider": "aws", "metering_batch_id": "6f4611df-8acf-48c8-9bf4-4a439ff59034", "billing_account_id": "customerAccount12345678"}', 'snapshot_rhel-for-x86-els-payg_vcpus', 'rhelemeter', 'efe37aa6-ac56-41df-8c8a-c260bd498f5b', '12345678', '6f4611df-8acf-48c8-9bf4-4a439ff59034', now());
```

2. Trigger the hourly tally
```
http POST ":8003/api/rhsm-subscriptions/v1/internal/tally/hourly?org=12345678" x-rh-swatch-psk:placeholder
```

Output:

```
HTTP/1.1 204 
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Connection: keep-alive
Date: Thu, 24 Oct 2024 10:55:16 GMT
Expires: 0
Keep-Alive: timeout=60
Pragma: no-cache
Set-Cookie: JSESSIONID=DA87CC9E6BE6F1D96153444E125F7151; Path=/; HttpOnly
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 0
traceresponse: 00-314bf601e06b4c76d199a71b0abc119b-bd13c0fb161d55fd-00
```

Note that "314bf601e06b4c76d199a71b0abc119b" is the trace ID of the whole operation. 
Running the splunk query `properties.traceId="314bf601e06b4c76d199a71b0abc119b"` will return all the logs within the spring boot and quarkus services.

### Sync Accounts (Organizations) by Swatch Conduit

Services:
- **swatch-system-conduit**

1. Sync the account org123
```
http :8005/api/rhsm-subscriptions/v1/internal/rpc/syncOrg   x-rh-swatch-psk:placeholder   org_id=org123
```
We should see the trace ID in the "traceresponse" response:
```
HTTP/1.1 200 
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Connection: keep-alive
Content-Length: 20
Content-Type: application/vnd.api+json
Date: Wed, 20 Nov 2024 06:12:47 GMT
Expires: 0
Keep-Alive: timeout=60
Pragma: no-cache
Set-Cookie: JSESSIONID=D91C434F673A9D97EDC73676A1A95126; Path=/; HttpOnly
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 0
traceresponse: 00-e861d11055c4675e01093c73ed606fa9-344caadf992b1d6c-01
```

The trace ID is the second token "e861d11055c4675e01093c73ed606fa9".

Searching in Splunk by this trace ID: `properties.traceId="e861d11055c4675e01093c73ed606fa9"`, we should see at least the following messages correlated: 

```
2024-11-20 06:58:55,295 [thread=http-nio-8005-exec-3] [INFO ] [org.candlepin.subscriptions.security.LogPrincipalFilter] self- Internal API: /api/rhsm-subscriptions/v1/internal/rpc/syncOrg requested by user: self
2024-11-20 06:58:43,581 [thread=rhsm-conduit-task-processor-0-C-1] [INFO ] [org.candlepin.subscriptions.conduit.InventoryController] - Finished page w/ offset '' of inventory updates for org org123, producing 1 updates
2024-11-20 06:58:43,610 [thread=rhsm-conduit-task-processor-0-C-1] [INFO ] [org.candlepin.subscriptions.conduit.InventoryController] - Finished page w/ offset 'consumer1id' of inventory updates for org org123, producing 1 updates
2024-11-20 06:58:43,660 [thread=rhsm-conduit-task-processor-0-C-1] [INFO ] [org.candlepin.subscriptions.conduit.InventoryController] - Host inventory update completed for org org123.
```

## Documentation

- [Quarkus OTel Tracing docs](https://quarkus.io/guides/opentelemetry-tracing)
- [Spring Boot Tracing docs](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)
- [OTel Spring Boot docs](https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/)
- [Configure the Java agent for Splunk Observability Cloud](https://docs.splunk.com/observability/en/gdi/get-data-in/application/java/configuration/advanced-java-otel-configuration.html)