# ADR 0003 — Playbook rules enforced by lint and ArchUnit from the first commit

Status: Accepted, 2026-10-01. Source: "New Service Playbook: Lessons from api-layer and DAL" (2026-10-01).

## Context

api-layer and the DAL accumulated 81 findings whose root causes are structural: opt-in safety, untyped contracts, god classes, guidance that contradicts the code. Each is cheap to prevent on day one and very expensive to retrofit. The playbook's checklist is the day-one standard for a new service.

## Decision

The checklist items a single repository controls are enforced mechanically, not by prose:

- `ArchitectureTest` (ArchUnit): shared packages never import features; no package cycles; constructor injection only; one logger API; one `ObjectMapper`; no ad hoc executors; no `Thread.sleep`; no `JsonNode` in contract packages.
- Checkstyle: 500 lines per main file, 1,000 per test file, 7 constructor parameters, no ticket keys in source, `@Disabled` requires a ticket, no SLF4J placeholders on the JBoss logger.
- Gradle: version catalog only, SNAPSHOT fails resolution, no `mavenLocal()`, zero test retries.
- CI: actions pinned by SHA, base and test images pinned by digest, gitleaks over the full history.
- Config: required infrastructure values have no default; HTTP policy denies by default; readiness checks a real dependency.
- Repository: `AGENTS.md` is the one guidance file; `CLAUDE.md` points to it; ADRs live here; root holds build files, `README.md`, `AGENTS.md`.

Items the playbook asks for that this repository cannot settle alone are recorded as asks: a shared Gradle convention plugin across services, Renovate on the organisation, Workload Identity Federation for CI deploys.

## Consequences

- SpotBugs `EI_EXPOSE_REP` and `EI_EXPOSE_REP2` are disabled once as policy; records copy collections in compact constructors instead.
- Stub code cannot name the ticket that will finish it; it names the behaviour ("selection and export job body").
- Operator endpoints are versioned under `/v1/` from day one, and error bodies are RFC 9457 problem+json with a stable `code`, rendered only by global mappers.
