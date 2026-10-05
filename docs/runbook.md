# Runbook: vendor-exchange-service, internal

Everything an operator or the next engineer needs to deploy, check and read this service. Commands run from the repository root unless noted. Project is always `certifyos-development`; the Terraform provider hardcodes it.

**Status 2026-10-06:** the export lane is complete and proven in CI end to end (decisions.md findings 69 to 89). The Terraform stack is validated and planned but held, pending the platform's move to Pulumi (finding 67). Sections 2 and 3 describe the shape and order a Pulumi component must reproduce; do not run `terraform apply` from this repository without a fresh decision. Section 9 is the first thing to run once the deployment and the egress changes exist.

## 1. What runs where

| Piece | Where | Notes |
| --- | --- | --- |
| api group | `vendor-exchange-api`, regional MIG, 2 x e2-small, us-central1 a/b/c | behind the HTTPS load balancer; PROACTIVE updates |
| worker group | `vendor-exchange-worker`, regional MIG, 1 x e2-standard-2 | JobRunr server and dashboard (:8000); no inbound; OPPORTUNISTIC updates |
| load balancer | `vendor-exchange-lb` address, cert `vendor-exchange-internal`, backend `vendor-exchange-api` | hostname `vendor-exchange.internal.certifyos.com`, 60 s backend timeout |
| health | `/q/health/live` (VM and LB health checks), `/q/health/ready` (dependencies) | ready is 503 while api-layer is unconfigured, by design |
| database | MongoDB Atlas, database `vendor_exchange` | URI in secret `vendor-exchange-mongodb-uri` |
| image | `us-central1-docker.pkg.dev/certifyos-development/vendor-exchange/vendor-exchange-service:<commit>` | both templates run the same tag |
| identities | `vendor-exchange-api@`, `vendor-exchange-worker@`, `vendor-exchange-pubsub-push@` | see section 5 for what each must be granted |

## 2. First deploy, in order

Each step finishes before the next. No VM boots without an image and a database URI this way.

1. `make tf-init` once per machine.
2. `make bootstrap-infra`: creates the Artifact Registry repository and the three secret containers only. Approve the plan.
3. Add the Mongo URI (interim: the shared `certifyos-app` user, same string file-ingestion uses):
   ```shell
   gcloud secrets versions access latest --secret=file-ingestion-mongodb-uri --project certifyos-development \
     | gcloud secrets versions add vendor-exchange-mongodb-uri --project certifyos-development --data-file=-
   ```
4. Start Docker, run `gcloud auth configure-docker us-central1-docker.pkg.dev -q` once, then `make push-image`.
5. `make create-template` writes the pushed tag into `terraform/internal/image.auto.tfvars` and creates the two templates. Then `make tf-apply` for everything else; approve the plan.
6. Wait for both groups: `gcloud compute instance-groups managed list --project certifyos-development --filter='name~vendor-exchange'` shows `IS_STABLE: Yes`.
7. `make smoke LB_IP=$(terraform -chdir=terraform/internal output -raw lb_ip)`; add `TOKEN_FILE=...` for the authenticated cases. Exit 0 pass, exit 2 up with a grant pending (labelled), exit 1 failure.
8. Commit `image.auto.tfvars`. A plain `make tf-plan` afterwards must be zero-change.

## 3. Routine deploy

```shell
make deploy            # push the image, rewrite image.auto.tfvars, apply templates and both groups
                       # the api group rolls at once (PROACTIVE, surge 3, unavailable 0)
make rollout-worker    # when jobrunr_jobs has no PROCESSING document; waits until stable
make smoke LB_IP=...   # or without LB_IP once the DNS record exists
git commit terraform/internal/image.auto.tfvars
```

Rollback: `make rollback-api PREVIOUS_TEMPLATE=<self link>` and the same for `worker`. Template self links are listed by `gcloud compute instance-templates list --filter='name~vendor-exchange'`; the newest two are the current ones.

Kill switch: set `VENDOR_EXCHANGE_ENABLED=false` on both templates (edit `compute.tf`'s `env_common`, `make create-template`, `make rollout-api`, `make rollout-worker`). The tick inserts nothing, jobs exit as no-ops, events are acknowledged and ignored. Rows stay where they are.

## 4. Pending hook-ups and how each one lands

Every external dependency has an interim that works today and a swap-in that is configuration or one Terraform variable. Tick the box when it lands and record the date in `docs/decisions.md`.

- [ ] **Auth0 machine client for api-layer** (A1). Interim: `ApiLayerTokenService` reports not configured; `/q/health/ready` lists `api-layer` DOWN. Swap-in: `gcloud secrets versions add vendor-exchange-apilayer-client-secret --data-file=-`, set `api_layer_client_id` in `variables.tf` (or a tfvars), `make create-template`, rollouts.
- [ ] **IAP access to the DAL and api-layer** (A2). Interim: operator calls answer `503 DAL_UNAVAILABLE`; the smoke script labels it "grant pending". Swap-in: none in code; the same deployment starts passing once `roles/iap.httpsResourceAccessor` is granted on `dal-service-internal` and `api-service-internal` to both VM accounts.
- [ ] **Service account and Pub/Sub bindings** (A3). Listed in `terraform/internal/iam.tf`. Needed before the first push delivery and for dead lettering.
- [ ] **Vendor bucket read** (A4). Bucket `certifyos-development-sftp-candor-health` exists; `vendor-exchange-worker@` needs `storage.objects.get` under `from/`. `FinishJob` reads object metadata there (never the file) and `RequestEgressJob` reads it when a prior attempt's cancel is refused.
- [ ] **Egress changes** (the design's Rollout asks: `npiFilter` above 100, `destination` on the request with the create-only copy, `correlationId` and counts in the object metadata, no manifest beside the file, the completion topic). Interim: the whole lane is proven against WireMock egress and a mocked bucket (`ExportLaneIT`). Swap-in: none in code; section 9 is the live verification once they land.
- [ ] **DNS** (A5). `vendor-exchange.internal.certifyos.com A <lb_ip output>` in Route 53. Until then the managed certificate stays `PROVISIONING` and the smoke script uses `LB_IP=`.
- [ ] **Dedicated Atlas user** (A6). Swap-in: a new version of `vendor-exchange-mongodb-uri`, then `make rollout-api` and `make rollout-worker`.
- [ ] **Egress completion topic** (A7). Swap-in: set `egress_events_topic` to the topic name and `make tf-apply`. This creates the push subscription and sets the two push settings on the api group, which opens `/internal/vendor-exports/egress-events` to verified tokens from `vendor-exchange-pubsub-push@`.
- [ ] **DAL permission rows** (A8). Swap-in: `permissions_enforce = true`, `make create-template`, rollouts. Endpoints then check `vendor-export:manage` and `vendor-export:read`.
- [ ] **Workload Identity for CI deploys** (A10). Interim: `make deploy` from a laptop.
- [ ] **Sentry** (optional). Swap-in: `gcloud secrets versions add vendor-exchange-sentry-dsn --data-file=-`, then rollouts; the startup script turns Sentry on when the DSN is present.
- [ ] **OpenTelemetry** (optional). Set `OTEL_SDK_DISABLED=false` and `OTEL_EXPORTER_OTLP_ENDPOINT` in `env_common`, `make create-template`, rollouts.

## 5. Grants and who holds them

| Grant | On | For | Holder |
| --- | --- | --- | --- |
| `roles/iap.httpsResourceAccessor` | `dal-service-internal`, `api-service-internal` | `vendor-exchange-api@`, `vendor-exchange-worker@` | DevOps |
| `roles/iam.serviceAccountTokenCreator` | `vendor-exchange-pubsub-push@` | `service-106861691435@gcp-sa-pubsub.iam.gserviceaccount.com` | DevOps |
| `roles/pubsub.publisher` | `vendor-exchange-egress-events-dlq` | the Pub/Sub service agent above | DevOps |
| `roles/pubsub.subscriber` | `vendor-exchange-egress-events` | the Pub/Sub service agent, `vendor-exchange-api@` | DevOps |
| `roles/storage.objectViewer` | `gs://certifyos-development-sftp-candor-health/from/` | `vendor-exchange-worker@` | Platform team (TS-111546) |
| Auth0 M2M client, audience `https://ng-api-stg.certifyos.com/` | Auth0 staging tenant | this service | Auth0 admin |
| Atlas user `vendor-exchange`, readWrite on `vendor_exchange` | `certifyos-pulumi` `mongodb.yaml` | this service | DevOps |

Already applied by Terraform: `logging.logWriter`, `monitoring.metricWriter`, `artifactregistry.reader` on the project for both VM accounts; `secretmanager.secretAccessor` on each secret for both.

## 6. Reading the service

**Health.** `curl -k --resolve vendor-exchange.internal.certifyos.com:443:<lb_ip> https://vendor-exchange.internal.certifyos.com/q/health/ready` lists every check with its status. Expected while pending: `MongoDB connection health check` UP, `api-layer` DOWN with reason "not configured".

**Logs.** Cloud Logging, resource type GCE VM instance, instance names `vendor-exchange-api-*` / `vendor-exchange-worker-*`. JSON lines carry `severity`, `loggerName`, `message` and `mdc`. One batch's whole history:

```
jsonPayload.mdc.exportBatchId="org-xyz-candor-2026-10-001"
```

**Metrics.** `/q/metrics` on any VM (port 8080, through an IAP tunnel) in Prometheus form. Names: `vendor_export_tick_duration_seconds`, `vendor_export_tick_schedules_due`, `vendor_export_schedules_by_state{state}`, `vendor_export_batches_by_state{state}`, `vendor_export_batches_created_total{tenant,vendor}`, `vendor_export_selection_practitioners{tenant,vendor}`, `vendor_export_selection_pages_total{tenant}`, `vendor_export_egress_wait_seconds{tenant}`, `vendor_export_egress_failed_total{tenant,reason}`, `vendor_export_completion_source_total{source}`, `vendor_export_event_rejected_total{reason}`, `vendor_export_reconcile_mismatch_total{tenant}`, `vendor_export_jobs_retries_total{job}`, `vendor_export_jobs_final_failures_total{job}`. Alerts E1 to E11 are defined in the design's Observability section and evaluate from the database or from the absence of an audit event.

**JobRunr dashboard.** `make tunnel-dashboard`, then open http://localhost:8000. Jobs are named `select <batchId>`, `request-egress <batchId>`, `deadline-check <batchId>`, `finish <batchId>`; recurring jobs are `vendor-export-tick` (cron `0 6 * * *` UTC) and `vendor-export-reconciler` (hourly).

**A job by batch id, in Mongo.** Job ids are deterministic: `JobIds.of(job, batchId, attempt)`. In `mongosh` against `vendor_exchange`:

```javascript
db.jobrunr_jobs.find({ jobName: /org-xyz-candor-2026-10-001/ }, { state: 1, jobName: 1, updatedAt: 1 })
db.vendor_export_batches.findOne({ _id: "org-xyz-candor-2026-10-001" })
db.vendor_export_events.find({ exportBatchId: "org-xyz-candor-2026-10-001" }).sort({ occurredAt: 1 })
db.jobrunr_background_job_servers.find({}, { firstHeartbeat: 1, lastHeartbeat: 1, running: 1 })
db.jobrunr_recurring_jobs.find({}, { _id: 1, scheduleExpression: 1, zoneId: 1 })
```

**Operator calls.** All under `/v1/vendor-exports`, platform token plus `tenant-id` header; section 7 has the procedures. Errors are `application/problem+json` with a stable `code`; the codes each endpoint can answer are listed on it in `openapi/openapi.yaml`.

## 7. Operator procedures

Every call needs a platform access token and the `tenant-id` header; the path tenant must be that tenant. Writes need `vendor-export:manage`, reads `vendor-export:read` (once A8 is on). Set `BASE=https://vendor-exchange.internal.certifyos.com` and `H=(-H "Authorization: Bearer $TOKEN" -H "tenant-id: $TENANT" -H "Content-Type: application/json")`.

**Enable a tenant for the vendor.** Create the schedule; creation provisions the tenant's egress template through api-layer (found by name `vendor-exchange candor certify-export-v1`, created from this repository's mappings CSV when absent) and enables the schedule.

```shell
curl "${H[@]}" -X PUT "$BASE/v1/vendor-exports/schedules/$TENANT/candor" -d '{
  "cadence": { "type": "monthly", "dayOfMonth": 1 },
  "timezone": "America/New_York",
  "selection": { "data.delegationStatus": { "in": ["Direct", "Delegated"] }, "credentialingStatus": { "eq": "Approved" } }
}'
```

201 with the schedule, its `egressTemplateId` and `nextDueAt` (local midnight on the day, never in the past). 409 `SCHEDULE_EXISTS` if one exists: `GET` it and send the body again with its `version` to replace it (200). Selection fields and operators are the design's table (`SELECTION_INVALID` lists every problem); cadence or timezone changes recompute `nextDueAt`, selection or template changes do not.

**Preview what the selection matches today** (no write, api-layer count with page size 1):

```shell
curl "${H[@]}" -X POST "$BASE/v1/vendor-exports/schedules/$TENANT/candor/preview" -d '{}'                      # the stored selection
curl "${H[@]}" -X POST "$BASE/v1/vendor-exports/schedules/$TENANT/candor/preview" -d '{"selection":{"credentialingStatus":{"eq":"Approved"}}}'
```

**Run now** (how a pilot's first export starts; the next tick is unaffected because `nextDueAt` advances as the tick would):

```shell
curl "${H[@]}" -X POST "$BASE/v1/vendor-exports/schedules/$TENANT/candor/run-now"
```

201 `{exportBatchId, jobId, nextDueAt}`. 409 `BATCH_EXISTS` when the current period already has its batch (supersede it instead), `SCHEDULE_DISABLED` when the schedule is disabled.

**Disable and enable** (reason mandatory; `catchUp` runs the one period missed while disabled, else the next future one):

```shell
curl "${H[@]}" -X POST "$BASE/v1/vendor-exports/schedules/$TENANT/candor/disable" -d '{"reason":"vendor outage"}'
curl "${H[@]}" -X POST "$BASE/v1/vendor-exports/schedules/$TENANT/candor/enable"  -d '{"reason":"vendor back","catchUp":true}'
```

**Read a batch.** The id is `<tenantId>-candor-<yyyy-MM>-<seq>`.

```shell
curl "${H[@]}" "$BASE/v1/vendor-exports?period=2026-10"             # the tenant's batches, newest first
curl "${H[@]}" "$BASE/v1/vendor-exports/$TENANT-candor-2026-10-001"
curl "${H[@]}" "$BASE/v1/vendor-exports/$TENANT-candor-2026-10-001/npis?limit=500"   # pass nextAfter back as after
```

What to expect in the document: `state` on the main path `SCHEDULED`, `NPIS_SELECTED`, `EGRESS_REQUESTED`, `EGRESS_COMPLETED`, `DELIVERED`; `selection.practitionersSelected` after selection; `egress.correlationId` (`<batchId>-r<attempt>`), `egress.destination`, `egress.requestedAt` after the request; `egress.completionSource` `EVENT`, `DEADLINE` or `PRIOR_ATTEMPT` and `egress.fileProducedBy` after completion; `file` (name, path, rowCount, bytes, `schemaVersion`), `reconciliation` (`registered`, `inFile`, `match`) and `deliveredAt` once delivered. `EMPTY` means the selection matched nobody; no file is produced for the period.

**Retry a failed batch** (from `FAILED` only; `failedStep` says where it goes back to):

```shell
curl "${H[@]}" -X POST "$BASE/v1/vendor-exports/$TENANT-candor-2026-10-001/retry" -d '{"reason":"egress fixed"}'
```

202 `{exportBatchId, attempt, jobId}`. `SELECT` restarts the selection from the saved page; `EGRESS` cancels the prior attempt's egress job, reuses the pinned template and the registered NPIs, and asks egress again under `-r<attempt>`. If the prior attempt had in fact placed the file, the retry finds it and finishes without a new request (`completionSource = PRIOR_ATTEMPT`).

**Supersede a delivered batch** (the file turned out wrong; from `DELIVERED` only):

```shell
curl "${H[@]}" -X POST "$BASE/v1/vendor-exports/$TENANT-candor-2026-10-001/supersede" -d '{"reason":"wrong template version"}'
```

201 `{exportBatchId, jobId}`: a new batch at `seq + 1` with the schedule's current selection; the old batch is `SUPERSEDED` and its file stays in the vendor folder (the vendor may have read it). The schedule's `lastBatchId` moves to the new batch; `nextDueAt` does not.

**Run the tick by hand** (same code as the 06:00 UTC job, answers its counts): `curl "${H[@]}" -X POST "$BASE/v1/vendor-exports/tick"`.

**Problem codes an operator sees.** 400 `SELECTION_INVALID`, `CADENCE_INVALID`, `TIMEZONE_INVALID`, `PERIOD_INVALID`, `REASON_REQUIRED`, `TEMPLATE_NOT_FOUND`, `INVALID_REQUEST`; 403 `TENANT_MISMATCH` (path tenant is not the header tenant); 404 `SCHEDULE_NOT_FOUND`, `BATCH_NOT_FOUND` (also for another tenant's batch); 409 `SCHEDULE_EXISTS`, `VERSION_STALE`, `ALREADY_ENABLED`, `ALREADY_DISABLED`, `SCHEDULE_DISABLED`, `BATCH_EXISTS`, `TEMPLATE_AMBIGUOUS`, `RETRY_NOT_ALLOWED`, `SUPERSEDE_NOT_ALLOWED`; 503 `API_LAYER_NOT_CONFIGURED`, `API_LAYER_UNAVAILABLE`, `DAL_UNAVAILABLE`.

## 8. When something is wrong

| Symptom | Where to look | Likely cause |
| --- | --- | --- |
| api group never healthy | serial console of a VM (`gcloud compute instances get-serial-port-output`) | image pull failing (registry grant, wrong tag), Mongo URI secret missing (boot refuses) |
| 503 `DAL_UNAVAILABLE` on every operator call | VM logs, `DAL user lookup failed` warn line | IAP grant pending (A2) or DAL down |
| ready lists Mongo DOWN | Atlas access list, VPC | VM not in `default` subnet range 10.128.0.0/9 |
| no `EXPORT_TICK_COMPLETED` by 06:30 UTC | `jobrunr_background_job_servers` heartbeat, worker VM | worker down across the tick; alert E1; the next tick catches up |
| batch stuck non-terminal | `vendor_export_events` for the batch, `jobrunr_jobs` by name | reconciler re-enqueues after `RECONCILER_STALE_MINUTES` and logs `EXPORT_RECONCILER_REENQUEUED`; alert E2 |
| batch `FAILED`, `failedStep SELECT` | `lastError`; `EXPORT_BATCH_FAILED` detail `cause JOB_RETRIES_EXHAUSTED` | api-layer unreachable or unconfigured through all retries; fix, then retry (section 7) |
| batch `FAILED`, `failedStep EGRESS` | `EXPORT_BATCH_FAILED` detail `cause`: `EGRESS_FAILED` (egress said so), `EGRESS_DID_NOT_FINISH` (48 h, cancelled), `FILE_NOT_FOUND` (completed but no object), `JOB_RETRIES_EXHAUSTED` (request never accepted) | ask egress, then retry; the registered NPIs are reused |
| `DELIVERED` with `reconciliation.match false` | `NPIS_RECONCILED` detail, `GET .../npis` against the file | egress dropped or duplicated rows; alert E6; supersede after the fix |
| `EXPORT_EGRESS_STALE` events | egress job status for the correlation id | egress slow; check 2 at request + 48 h fails the batch if still running |
| `EXPORT_EVENT_REJECTED` events | detail `reason` `UNPARSEABLE`, `UNKNOWN_SCHEMA`, `TENANT_MISMATCH` | egress changed the event schema, or a subscription filter is wrong; alert E5 |
| push endpoint 401 | `PUSH_*` code in the problem body | not configured (A7 pending), wrong audience, token not from the push account |

## 9. First live verification

Run this once, in order, as soon as the Pulumi deployment exists and the egress changes have landed. It is the first real traffic the lane sees; `ExportLaneIT` has proven the same sequence against WireMock. Use a pilot tenant the vendor contract names; `TENANT`, `TOKEN`, `BASE` and `H` as in section 7.

1. Readiness lists `api-layer` UP (A1 and A2 landed): `curl -s "$BASE/q/health/ready" | jq .checks`.
2. Create the schedule disabled-safe: `PUT` as in section 7, then `POST .../disable -d '{"reason":"pilot: run by hand first"}'`. Confirm in api-layer that the template `vendor-exchange candor certify-export-v1` exists for the tenant and its mappings CSV is this repository's `certify-export-v1.mappings.csv` (the first live create is the gate for the attribute keys, finding 69).
3. Preview: `POST .../preview -d '{}'`. The count must be the one the vendor expects for the pilot; adjust the selection with `PUT` and the `version` until it is.
4. Enable and run: `POST .../enable -d '{"reason":"pilot","catchUp":false}'`, then `POST .../run-now`. Keep the `exportBatchId`.
5. Watch the batch reach `EGRESS_REQUESTED` within a minute (`GET .../$ID` or the JobRunr dashboard through `make tunnel-dashboard`). Check the egress job in its Activity Center under the correlation id `$ID-r1`.
6. Watch for `EGRESS_COMPLETED` by the event (`completionSource EVENT`) and then `DELIVERED`. If the event does not arrive within six hours the deadline check completes it (`DEADLINE`) and `EXPORT_EVENT_MISSED` is written: that is the push subscription (A7) to fix, not the batch.
7. Verify the object and its metadata, which is what `FinishJob` read:
   ```shell
   gcloud storage objects describe "gs://certifyos-development-sftp-candor-health/from/$TENANT/${TENANT}_${ID}_$(date -u +%Y%m%d).csv"      --project certifyos-development --format='value(size,metadata)'
   ```
   `metadata.complete=true`, `metadata.correlationId=$ID-r1`, `metadata.totalRecords` equal to `reconciliation.registered` in the batch, and no `.manifest.json` beside the file.
8. Read the audit trail and compare with finding 89's first sequence: in `mongosh`, `db.vendor_export_events.find({ exportBatchId: "$ID" }).sort({ _id: 1 }).map(e => e.type)`.
9. Record the outcome as the next numbered finding in `docs/decisions.md`, with the batch id, the object path and the trail; tick the boxes in section 4 that the run proved.
