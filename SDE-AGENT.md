# SDE Agent instructions — retail-store-sample-app

Additional instructions for the SDE Agent when working autonomously in this repository.
This file is (1) appended to the agent's prompt as guidance and (2) parsed for the
circuit-breaker limits below.

## Circuit breakers (parsed; a repo may only tighten below the system caps)

maxTurns: 120
maxBudgetUsd: 50
ciVerifyTimeoutSeconds: 300

## Instructions

1. Think Before Coding — state assumptions, ask one clarifying question, wait for confirmation before writing a single line
2. Simplicity First — minimum code that solves exactly this problem, no speculative features or unnecessary abstractions
3. Surgical Changes Only — touch only what was asked, never refactor adjacent code, one diff one scope
4. Goal-Driven Execution — restate the task as verifiable success criteria and loop until every criterion is met

## How to work here

- **Scope changes tightly to the task.** Touch only the service(s) the task concerns; do not
  refactor across services or edit unrelated code.
- **Match existing conventions per service** — Java code follows the existing package/style;
  TypeScript is formatted with Prettier (`.prettierrc`) and linted with ESLint.
- **Do NOT modify** deployment/infra unless the task explicitly asks: `terraform/`, Helm charts,
  `docker-compose*.yaml`, `.github/`, release config (`release-please-config.json`), `renovate.json`.
- Keep commits focused; write a clear message describing the change.

## Definition of done

- The change addresses the task and nothing more.
- The relevant service builds; the relevant tests run (and you report what you ran + the outcome).
- No secrets, credentials, or infra/deployment files changed unless explicitly requested.

## Notes for future runs (CI tips)

- The `ui` service requires **Java 21** (system default may be 17). Use
  `JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto` when building/testing locally.
- UI unit tests: `cd src/ui && ./mvnw test -DexcludedGroups=integration`. Lint: `./mvnw checkstyle:checkstyle`.
- The `hooks` CI job runs `yarn prettier --check` on changed `.java`/`.md`/`.xml`/`.yaml` files.
  Run `yarn prettier --write <files>` (from repo root) before committing or it will fail.
- PR titles must be semantic (Conventional Commits, e.g. `feat(ui): ...`) — enforced by the PR workflow.
