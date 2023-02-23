# System Conduit (fka. rhsm-conduit)

See general developer setup instructions in the repo root.

### Environment Variables

The following environment variables are specific to the system-conduit service:

* `RHSM_USE_STUB`: Use RHSM API stub
* `RHSM_URL`: RHSM service URL
* `RHSM_KEYSTORE`: path to keystore with client cert
* `RHSM_KEYSTORE_PASSWORD`: RHSM API client cert keystore password
* `RHSM_BATCH_SIZE`: host sync batch size
* `RHSM_MAX_CONNECTIONS`: maximum concurrent connections to RHSM API
* `HOST_LAST_SYNC_THRESHOLD`: reject hosts that haven't checked in since this duration (e.g. 24h)
* `INVENTORY_ENABLE_KAFKA`: whether kafka should be used (inventory API otherwise)
* `INVENTORY_HOST_INGRESS_TOPIC`: kafka topic to emit host records
* `INVENTORY_ADD_UUID_HYPHENS`: whether to add missing UUID hyphens to the Insights ID
* `CONDUIT_KAFKA_TOPIC`: topic for rhsm-conduit tasks
* `CONDUIT_KAFKA_GROUP_ID` rhsm-conduit kafka consumer group ID
