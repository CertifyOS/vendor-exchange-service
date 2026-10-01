# Decisions

Findings and deviations, numbered, in order. Each one says what was checked and what changed.
Plan: `cos-docs/directory-accuracy/vendor-export/scaffolding-implementation-plan.md` (CP-39604).

## Step 0: repository from template, pins, roles, CI (2026-10-01)

1. **Origin.** Created from the org template `CertifyOS/quarkus-base`, the same template `file-ingestion-service` started from (its initial commit is byte-for-byte the template tree). Template leftovers removed as file-ingestion did: `Dockerfile.native`, `Dockerfile.native-micro`, `Dockerfile.legacy-jar`, the Spanner config, the generic `ApplicationLivenessCheck` and its test, `src/native-test`.

2. **Pins.** Quarkus 3.33.3, JobRunr 8.8.2, Gradle wrapper 8.9, Java 21, unchanged from file-ingestion's gate. The dependency list is file-ingestion's minus the file-parsing libraries (`fastexcel-reader`, `commons-csv`, `re2j`) and minus `testcontainers:gcloud` (no Pub/Sub emulator test planned; the push handler is tested with direct HTTP).

3. **Quality gates on from the first commit.** CI runs `./gradlew check`, which includes Spotless, Checkstyle, SpotBugs, unit and integration tests. file-ingestion left the three static gates out and carries a 160-violation backlog (its findings 23, 55). Two Checkstyle rules from the template conflict with CDI and were adjusted rather than suppressed per file:
   - `VisibilityModifier`: `jakarta.inject.Inject` added to `ignoreAnnotationCanonicalNames`, so package-private `@Inject` fields pass. Quarkus warns on private injected fields; package-private is its documented recommendation.
   - `DesignForExtension` removed. CDI beans need overridable public methods for client proxies; the rule flags every one of them.
   `config/spotbugs/exclude.xml` is empty: the template's filter named `com.certifyos.dal.*` classes and excluded nothing here. Records holding a `List` or `Map` copy defensively in a compact constructor instead of being excluded from `EI_EXPOSE_REP`.

4. **No `pre-push` hook.** The template's hook ran the full test suite on every push. CI is the gate; local runs are compile and lint (`make compile`, `make lint`). `commit-msg` (Conventional Commits) is kept.

5. **Role check.** `config/RoleProfileCheck` is file-ingestion's class with the package changed: exactly one of `api` or `worker` must be an active profile. `%worker` is the only profile that turns the JobRunr background server on; `%api` has no keys yet and exists so `prod,api` is a valid, explicit role.

6. **Checkstyle suppressions.** The template's suppressions named a DAL generator path and three DAL classes. Replaced with generic rules for `src/test` and `src/integrationTest` (Javadoc, static imports, visibility, `System.out`), generated sources, and non-code files.

7. **Step 0 gate.** PR #1, CI run `36853441920`: `./gradlew check` green in 5 min 1 s on the first run (Spotless, Checkstyle on three source sets, SpotBugs, `RoleProfileCheckTest` 4 of 4, `integrationTest` with no sources yet). `./gradlew quarkusBuild` produced `build/quarkus-app/quarkus-run.jar` locally. The image build was not run locally (no Docker daemon on this machine at the time); it is exercised by `make build-image` in Step 8 before the first push to Artifact Registry. One Checkstyle rule bit on the first local run: `LocalVariableName` refuses single-letter locals (`^[a-z][a-zA-Z0-9]+$`), so `catch (Exception e)` is fine (parameter) but `var e = assertThrows(...)` is not.

8. **Branch protection on `main`.** Set by API on day one: required status check `check` (strict), one approving review, code-owner review, dismiss stale reviews, conversation resolution, no force push or deletion. `file-ingestion-service` has no protection on `main` (checked, 404 on the protection endpoint), so this is a deliberate difference rather than a copy.
