# vendor-exchange-service

Directory accuracy vendor exchange. Phase 1 selects practitioners per tenant on a schedule, asks the egress service to build the vendor file, and records what was sent. Phase 2 ingests the vendor's response in the same service.

Design: Confluence "CP-39602 - Design: Directory accuracy vendor export". Working rules: `AGENTS.md`. Decisions: `docs/adr/` (settled) and `docs/decisions.md` (running log).

## Prerequisites

- Java 21 and Docker (Testcontainers and the image build).
- For local runs, a MongoDB replica set and the values marked required in `.env.example`.

## Setup

```shell
make bootstrap   # git hooks, copies .env.example to .env
make dev         # api role; make dev ROLE=worker for the JobRunr server
```

## Everyday

```shell
make compile     # compile every source set, run nothing
make lint        # Spotless, Checkstyle, SpotBugs
make test        # unit tests (plain JUnit, ArchUnit)
make check       # everything CI runs, integration tests included
make build       # fast-jar and the linux/amd64 image
```

Deploy targets and `terraform/internal/` arrive with Step 8 of the scaffolding plan.
