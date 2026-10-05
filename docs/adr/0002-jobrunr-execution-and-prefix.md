# ADR 0002 — JobRunr owns execution; its collections carry the `jobrunr_` prefix

Status: Accepted, 2026-10-01. Source: design doc decisions D3, D4, D13; decisions log 16.

## Context

Every job in the export lane talks to one external system and needs retry with backoff, resumption after a crash, a failure record and an operator view. JobRunr provides all of that on top of the service's own MongoDB. Its storage provider creates five collections named `jobs`, `recurring_jobs`, `background_job_servers`, `metadata` and `migrations` when no prefix is set; the design doc names them with a `jobrunr_` prefix.

## Decision

- JobRunr (8.8.2, `quarkus-jobrunr`) runs the jobs, the recurring tick and reconciler, and the retry policy. The batch row is the truth: every job checks the row state first and exits as a no-op on mismatch.
- `quarkus.jobrunr.database.table-prefix=jobrunr_`, so the collections are `jobrunr_jobs` and so on, matching the design doc and marking ownership beside `vendor_export_*` in the same database.
- Our code never touches those collections directly; it goes through the JobRunr API. The only reader of `jobrunr_background_job_servers` is the heartbeat alert.

## Consequences

- A final-failure filter and an hourly reconciler close the two gaps JobRunr cannot see (retries exhausted; row written but job never enqueued).
- The filter must be registered through a `StartupEvent` observer with `@Priority(APPLICATION)`, because quarkus-jobrunr does not scan CDI filters and `JobRunrStarter` observes the same event without a priority.
- `WorkerRoleBootIT` asserts the prefixed collection names; a future prefix change fails that test.
