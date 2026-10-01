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

## Step 1: configuration and test profiles (2026-10-01)

9. **`VendorExchangeConfig` is the closed `vendor-exchange.*` mapping.** Six keys have no default (`vendor-bucket`, `egress.url`, `egress.iap-client-id`, `dal.url`, `dal.iap-client-id`, `api-layer.url`); a missing one fails the boot with its name in the message (`VendorExchangeConfigTest.everyMissingRequiredKeyIsNamed`). Any undeclared key under the prefix is refused (`unknownKeyUnderThePrefixIsRefused`), the trap file-ingestion hit with leftover `file-ingestion.role` keys (its finding 16). Test values live in `TestConfig.common()`, shared by both profiles, because the mapping is validated on every boot whether or not a test injects it.

10. **Swap-in keys are `Optional` and map to `${ENV:}`.** `api-layer.client-id`, `api-layer.client-secret`, `pubsub.push-service-account`, `pubsub.push-audience` resolve to an empty string when the environment variable is unset, and SmallRye reads an empty value as `Optional.empty()`. Asserted twice: on the mapping alone (`optionalKeysAreAbsentWhenUnsetOrEmpty`) and through the real `application.properties` under the api profile (`ApiRoleBootIT.swapInKeysResolveToAbsentThroughTheEmptyDefault`). The api-layer client secret stays a plain environment variable until Step 8 decides between a `${sm//...}` reference (which fails the boot when the secret has no version) and a placeholder version.

11. **JobRunr retries come from the mapping.** `quarkus.jobrunr.jobs.default-number-of-retries=${vendor-exchange.job-retries}` with `vendor-exchange.job-retries=${JOB_RETRIES:8}` declared as a property, because a `@WithDefault` on the mapping is not a property value another expression can expand. Same pattern for every default the deployment may override.

12. **Dashboard on the worker profile, off in the worker test profile.** `%worker.quarkus.jobrunr.dashboard.enabled=true` on port 8000 for the real VM (reached through an IAP SSH tunnel; no firewall rule). `WorkerTestProfile` overrides it to `false` so a test JVM never binds the port. `WorkerRoleBootIT` asserts both the server flag and the dashboard flag, and waits for the server's heartbeat row in `jobrunr_background_job_servers` as the proof the worker role runs.

13. **Telemetry exporters off at the base level.** `quarkus.otel.sdk.disabled=true` and `quarkus.log.sentry.enabled=false` until Step 7, so no boot logs connection errors against `localhost:4317`. `quarkus.log.console.json.enabled` is the current key (the bare `quarkus.log.console.json` file-ingestion uses is the deprecated spelling); JSON off under `%dev` and `%test`.

14. **HTTP policy declared now, endpoints later.** `/q/*` permit, `/internal/*` permit (the push filter does its own 401 in Step 5), `/vendor-exports/*` authenticated. `ApiRoleBootIT.operatorPathsRequireAuthentication` gets a 401 on `/vendor-exports/schedules` with the OIDC tenant disabled and no resource behind the path, which is the HTTP-layer policy working on its own. This is the coverage file-ingestion recorded as missing (its finding 20); it holds here because the assertion needs no token at all.

15. **Step 1 gate.** CI run `36855962179` green in 2 min 36 s: unit 10 of 10 (`RoleProfileCheckTest` 4, `VendorExchangeConfigTest` 6), integration 7 of 7 (`ApiRoleBootIT` 5, `WorkerRoleBootIT` 2) against Testcontainers `mongo:7.0`. Both roles boot from the same `application.properties`; the empty-means-absent rule, the 401 on `/vendor-exports/*`, and the worker heartbeat row are all proven by a running Quarkus, not by inspection.
