# HANDOFF

State of `vendor-exchange-service` at the end of the scaffolding subtask (CP-39604), and how work on it is done. Read `AGENTS.md` first; this file says where things stand, that one says how to work.

## Where things stand (2026-10-05)

- Steps 0 to 7 of the scaffolding plan are built, tested in CI and recorded: repository and gates, configuration and profiles, persistence, audit trail, JobRunr wiring, inbound auth chain and HTTP skeleton, outbound identity and client interfaces, observability baseline. `docs/decisions.md` findings 1 to 63 hold the evidence for each gate.
- Step 8 is built and planned, with the live apply pending the engineer: `terraform plan` is clean at 35 resources, the image is not yet pushed, the groups do not exist yet. `docs/runbook.md` section 2 is the exact order. Finding 67 is reserved for the live evidence.
- Step 9 is this file, the README and the runbook. The Jira ticket's Implementation Details and points are written from the same text.
- Branch `feature/CP-39604-scaffolding-step-0`, pull request #1, every commit CI green. `main` is protected and still holds the template.
- Tests: 92 unit, 75 integration, 0 failures. Every design index, every audit event type, every metric name, every endpoint and every client contract from the design exists in code.

## What is deliberately a stub

- The four job bodies (`SelectJob`, `RequestEgressJob`, `DeadlineCheckJob`, `FinishJob`) load and check the batch, then throw `UnsupportedOperationException`. The tick finds due schedules and records the fact but creates no batch rows yet. These are the export lane's work (CP-39605).
- `GET /v1/vendor-exports` and `GET /v1/vendor-exports/schedules` answer empty lists; the schedule write endpoints from the design are not built.
- The push endpoint parses, logs and acknowledges; matching the event to a batch is the export lane's.
- `EgressClient` and `ApiLayerClient` are interfaces with typed contracts and tests against WireMock; nothing calls them yet.

## What the next subtask starts from

- Fill a job body: open `JobLogContext` is already done by the handler; `BatchJobSupport.loadExpecting` returns the batch in the expected state; `Transactions.run` with `ExportBatchRepository.transition` and `AuditRepository.write(session, ...)` is the only way to move state; `JobEnqueuer` enqueues the next job after the transaction, never inside it.
- The tick: `TickJob.run` has the due schedules; per schedule, one transaction: `ExportBatchRepository.insert`, `ScheduleRepository.advance`, `EXPORT_BATCH_SCHEDULED`; then `JobEnqueuer.select`.
- Selection: `ApiLayerClient.practitionerFindMany(tenant, filter JSON, page, 100)`; `SelectionCriteria` holds the clauses and still needs a `toApiLayerFilter()`; `ExportNpiRepository.upsertAll` is idempotent by `<batchId>|<npi>`.
- Egress: `EgressClient.export` with `EgressExportRequest`; `rowExpansionKeys` is a JSON array string on the wire; both 202 shapes parse; 409 arrives as a client exception with status 409.
- Metrics: call `VendorExchangeMetrics` from the lane; names already exist. Audit: `AuditEvent.forBatch(type, tenant, vendor, batchId, attempt)`.
- Nothing in `config/`, `persistence/`, `auth/`, `http/`, `clients/`, `metrics/` or `terraform/` needs to change for the lane to work. If it does, that is a finding.

## Pending outside this repository

Listed with owners in `docs/runbook.md` sections 4 and 5: Auth0 machine client, IAP grants on the DAL and api-layer backends, service-account and Pub/Sub bindings, vendor bucket read, DNS record, dedicated Atlas user, egress completion topic, DAL permission rows, Workload Identity for CI, design-doc edits (names `vendor-exchange-*`, paths under `/v1`, problem+json, push verifier wording, `rowExpansionKeys` as a string, egress response shapes, egress internal without IAP, api-layer behind IAP). Also: `@suhasini-certify` needs write access on this repository for the CODEOWNERS entry to count.

## How work is done here

- One step at a time, with a gate: every step ends with CI green and a numbered finding in `docs/decisions.md` that states what was proven and what was not. Nothing moves to the next step without that.
- Nothing is assumed from memory about a framework: check the jar (`javap`), the source of the sibling service, or a running test. Findings 50, 53, 56, 61 are each a case where the assumption was wrong and the check found it.
- The new-service playbook (Claude Doc "New Service Playbook: Lessons from api-layer and DAL") is the standard; its rules are enforced by ArchUnit and Checkstyle, not remembered.
- Locally: `make compile` and `make lint`, plus the unit test classes a change touches. The full suite runs in CI; integration tests need Docker.
- External writes (Jira, Confluence, access grants, `terraform apply`) are drafted or planned here and run by the engineer.
- Every deviation from the plan or the design is written down the day it happens, with the reason, in `docs/decisions.md`; a settled, hard-to-reverse choice becomes an ADR.
