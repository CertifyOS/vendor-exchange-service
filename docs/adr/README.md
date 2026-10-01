# Architecture decision records

One file per decision that shapes the service and would be expensive to reverse. `docs/decisions.md` is the running log of findings while building; an ADR is the settled outcome. Format: context, decision, consequences. Numbered, never edited after acceptance; a change is a new ADR that supersedes the old one.

| # | Title | Status |
| --- | --- | --- |
| 0001 | One image, two roles by Quarkus profile on two GCE instance groups | Accepted |
| 0002 | JobRunr owns execution; its collections carry the `jobrunr_` prefix in the service database | Accepted |
| 0003 | Playbook rules enforced by lint and ArchUnit from the first commit | Accepted |
