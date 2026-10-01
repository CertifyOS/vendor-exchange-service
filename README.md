# vendor-exchange-service

Directory accuracy vendor exchange. Phase 1 (CP-39602) selects practitioners per tenant on a schedule, asks the egress service to build the vendor file, and records what was sent. Phase 2 (CP-40437) ingests the vendor's response in the same service.

Design: Confluence "CP-39602 - Design: Directory accuracy vendor export". Scaffolding plan: `cos-docs/directory-accuracy/vendor-export/scaffolding-implementation-plan.md`.

## Shape

- One image, two roles selected by Quarkus profile: `api` (HTTP, behind the load balancer) and `worker` (JobRunr server, no inbound traffic). `RoleProfileCheck` refuses to boot with neither or both.
- Quarkus 3.33.3, JobRunr 8.8.2, MongoDB (database `vendor_exchange`), Java 21.
- Same topology as `file-ingestion-service`; its `docs/decisions.md` is the reference for every platform lesson this repo inherits.

## Prerequisites

- Java 21, Docker (Testcontainers and the image build).
- For local runs, a MongoDB replica set: see `.env.example`.

## First-time setup

```shell
make install-git-hooks
cp .env.example .env
```

## Running locally

```shell
make dev-api      # QUARKUS_PROFILE=dev,api
make dev-worker   # QUARKUS_PROFILE=dev,worker
```

## Checks

```shell
make compile      # compile every source set, run nothing
make lint         # Spotless, Checkstyle, SpotBugs
make check        # everything CI runs (unit + integration tests included)
```

## Build

```shell
make build-image  # fast-jar + linux/amd64 image
```

Deploy targets and `terraform/internal/` are added in Step 8 of the scaffolding plan.

## Documents

- `docs/decisions.md`: every finding and deviation, in order.
- `docs/runbook.md`: deploy order, hand grants, swap-in points (Step 9).
