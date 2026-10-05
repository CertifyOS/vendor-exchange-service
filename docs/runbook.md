# Runbook: vendor-exchange-service, internal

Everything an operator or the next engineer needs to deploy, check and read this service. Commands run from the repository root unless noted. Project is always `certifyos-development`; the Terraform provider hardcodes it.

**Status 2026-10-05:** the Terraform stack is validated and planned but held, pending the platform's move to Pulumi (decisions.md finding 67). Sections 2 and 3 describe the shape and order a Pulumi component must reproduce; do not run `terraform apply` from this repository without a fresh decision.

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
- [ ] **Vendor bucket read** (A4). Bucket `certifyos-development-sftp-candor-health` exists; `vendor-exchange-worker@` needs `storage.objects.get` under `from/`. Needed by `FinishJob` (export lane).
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

**Operator calls.** All under `/v1/vendor-exports`, platform token plus `tenant-id` header. `GET /schedules` and `GET /` list (empty until the export lane fills them); `POST /tick` runs the daily tick inline and returns its counts. Errors are `application/problem+json` with a stable `code`: `TENANT_REQUIRED`, `TENANT_UNRESOLVED`, `TENANT_FORBIDDEN`, `PERMISSION_DENIED`, `DAL_UNAVAILABLE`, `NOT_FOUND`, `METHOD_NOT_ALLOWED`, `INTERNAL`; on the push endpoint `PUSH_NOT_CONFIGURED`, `PUSH_TOKEN_REQUIRED`, `PUSH_TOKEN_REJECTED`.

## 7. When something is wrong

| Symptom | Where to look | Likely cause |
| --- | --- | --- |
| api group never healthy | serial console of a VM (`gcloud compute instances get-serial-port-output`) | image pull failing (registry grant, wrong tag), Mongo URI secret missing (boot refuses) |
| 503 `DAL_UNAVAILABLE` on every operator call | VM logs, `DAL user lookup failed` warn line | IAP grant pending (A2) or DAL down |
| ready lists Mongo DOWN | Atlas access list, VPC | VM not in `default` subnet range 10.128.0.0/9 |
| no `EXPORT_TICK_COMPLETED` by 06:30 UTC | `jobrunr_background_job_servers` heartbeat, worker VM | worker down across the tick; alert E1; the next tick catches up |
| batch stuck non-terminal | `vendor_export_events` for the batch, `jobrunr_jobs` by name | reconciler re-enqueues after `RECONCILER_STALE_MINUTES`; alert E2 |
| push endpoint 401 | `PUSH_*` code in the problem body | not configured (A7 pending), wrong audience, token not from the push account |
