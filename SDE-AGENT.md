# SDE Agent instructions — retail-store-sample-app

Additional instructions for the SDE Agent when working autonomously in this repository.
This file is (1) appended to the agent's prompt as guidance and (2) parsed for the
circuit-breaker limits below.

## Circuit breakers (parsed; a repo may only tighten below the system caps)

maxTurns: 60
maxBudgetUsd: 20
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

## CI notes (for future runs)

- The `ui` service (Spring Boot) requires **Java 21** (per root `mise.toml`); the default `java`
  on PATH may be 17. Build/test/run with `JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto`.
- UI test command: `./mvnw test -DexcludedGroups=integration` (from `src/ui`). Lint: `./mvnw checkstyle:checkstyle`.
- CI's `hooks` job runs `prettier --check` on staged `.java`/`.xml`/etc. files; run
  `yarn prettier --write <files>` (needs `yarn install` first) before committing to pass it.
- PR titles must be **semantic** (`amannn/action-semantic-pull-request`), e.g. `feat(...)`, `fix(...)`.
- The `AI PR Review (Claude Code on AgentCore)` check invokes an AWS Bedrock AgentCore runtime and
  is prone to environmental failures (`Connection was closed before we received a valid response`);
  this is infra-side and not fixable from the PR branch. The required checks are `PR` and `E2E Test`.
