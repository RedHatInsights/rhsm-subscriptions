# ADR-001: Remove pre-cleanup from the global contract sync

**Status:** Accepted  
**Deciders:** @karshah @tlencion
**Date:** 2026-03-10

---

## Context

The per-org sync endpoint (`/internal/rpc/sync/contracts/{org_id}`) accepts an optional
`is_pre_cleanup` flag. When `true`, it deletes any contracts for that org where
`billing_provider_id` is null/empty or `end_date` is null before fetching fresh data from
the upstream partner API. The intent is to let operators clean up incomplete records that
were partially written and never fully populated.

The global sync endpoint (`/internal/rpc/syncAllContracts`) was hardcoding this flag to
`true` for every org it processed.

---

## Problem

Hardcoding `is_pre_cleanup=true` in the global sync creates two risks:

1. **Unnecessary data deletion.** The global sync is a routine reconciliation job, not a
   repair tool. Deleting records before every upsert is a side effect that is not needed
   for correct reconciliation — the upsert logic already handles existing records by
   matching on `start_date` and provider-specific IDs.

2. **Loop iteration hazard.** `syncAllContracts` iterates over every `ContractEntity` row,
   not over distinct org IDs. An org with two contracts is therefore processed twice. On
   the second pass, the pre-cleanup would run again for the same org, potentially deleting
   contracts that were freshly re-created during the first pass if any field was missing at
   the point of deletion.

---

## Decision

`syncAllContracts` passes `isPreCleanup=false` to the per-org sync for every org it visits.

The pre-cleanup capability remains available on the per-org endpoint as an opt-in parameter
for operators who need to repair a specific org's data. It is not applied globally.

---

## Consequences

- The global sync is purely additive/updating — it never deletes before upserting.
- Contracts with incomplete data (null `billing_provider_id` or null `end_date`) are not
  automatically cleaned up by the global sync; an operator must explicitly call the per-org
  endpoint with `is_pre_cleanup=true` to repair them.
- Idempotency of the global sync is improved: running it multiple times on the same data
  produces the same result with no risk of accidental deletion.
