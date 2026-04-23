# Sumo Logic: org failures during contracts sync all

Contract sync all enqueues one Kafka message per org; only `ContractSyncService` publishes to the contract-sync topic, so **failure lines from `ContractSyncTaskConsumer` always refer to that fan-out** (not the HTTP single-org sync path).

Logs include the prefix `contract-sync-all:` so monitors can match without ambiguity.

## Search query (adjust `namespace`)

```
_index="rh_rhsm" namespace="rhsm-prod" _sourcecategory="swatch" _sourcehost=swatch-contracts-service*
("contract-sync-all: Contract sync failed for orgId=" OR "contract-sync-all: Contract sync threw exception for orgId=")
| parse regex "contract-sync-all: Contract sync failed for orgId=(?<org_id>[^:]+): (?<failure_reason>.*)" nodrop
| parse regex "contract-sync-all: Contract sync threw exception for orgId=(?<org_id>[^\s]+)" nodrop
| fields _messagetime, org_id, failure_reason, _raw
```

Use `namespace="rhsm-stage"` (or your target) for non-production.

## What fires the alert

| Log substring | Meaning |
|----------------|---------|
| `contract-sync-all: Contract sync failed for orgId=` | Sync returned a failure status (e.g. partner API / no upstream contracts for that org). |
| `contract-sync-all: Contract sync threw exception for orgId=` | Uncaught exception while syncing that org. |
