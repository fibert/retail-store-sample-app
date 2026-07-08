# SDE Agent instructions — retail-store-sample-app

Additional instructions for the SDE Agent when working autonomously in this repository.
This file is (1) appended to the agent's prompt as guidance and (2) parsed for the
circuit-breaker limits below.

## Circuit breakers (parsed; a repo may only tighten below the system caps)

maxTurns: 60
maxBudgetUsd: 20

## What this repo is

A polyglot microservices retail sample managed as an **Nx monorepo**. Each service lives under
`src/`:

| Service    | Path             | Stack                    | Build / test tool |
|------------|------------------|--------------------------|-------------------|
| ui         | `src/ui`         | Java (Spring)            | Maven (`pom.xml`) |
| cart       | `src/cart`       | Java (MongoDB/DynamoDB)  | Maven (`pom.xml`) |
| orders     | `src/orders`     | Java                     | Maven (`pom.xml`) |
| catalog    | `src/catalog`    | Go                       | `go` / `Makefile` |
| checkout   | `src/checkout`   | Node (NestJS/TypeScript) | yarn + Nx + Jest  |

Educational sample only — not production. It's a fork of `aws-containers/retail-store-sample-app`.

## How to work here

- **Scope changes tightly to the task.** Touch only the service(s) the task concerns; do not
  refactor across services or edit unrelated code.
- **Match existing conventions per service** — Java code follows the existing package/style;
  TypeScript is formatted with Prettier (`.prettierrc`) and linted with ESLint.
- **Do NOT modify** deployment/infra unless the task explicitly asks: `terraform/`, Helm charts,
  `docker-compose*.yaml`, `.github/`, release config (`release-please-config.json`), `renovate.json`.
- Keep commits focused; write a clear message describing the change.

## Building & testing

This is an Nx monorepo. Prefer running the check for the service you changed:

- **checkout (Node):** from `src/checkout`, run `yarn install` then the relevant script
  (`yarn build`, `yarn lint`, `yarn test`). Note: unit `test` is currently a no-op (`exit 0`);
  meaningful tests are `test:integration` (Jest e2e). Don't claim tests passed if you only ran the
  no-op — say what you actually ran.
- **catalog (Go):** from `src/catalog`, run `go build ./...` and `go test ./...` (or the `Makefile`
  targets if present).
- **ui / cart / orders (Java):** from the service dir, `mvn -q -B verify` (or `mvn test`) if Maven
  and a JDK are available in the build environment. If the toolchain isn't installed, note that you
  could not run the Java build rather than assuming success.
- **Always run the check that corresponds to the code you changed**, and report the actual result.

## Running the app (optional; usually not needed for code tasks)

- The runnable compose lives at `src/app` and needs a DB password:
  `docker compose --project-directory src/app up --build --detach --wait` (npm: `yarn compose:up`).
- NOTE: the SDE Agent's automatic dev-deploy only brings up the **root** `docker-compose.yaml`,
  which requires a `DB_PASSWORD` env var it does not set — so the automatic dev deployment may be
  partial or skipped. This is non-fatal; only stand the app up yourself if the task specifically
  requires a running instance (e.g. reproducing a runtime bug), using the `src/app` command above.

## Known CI state (factual, for future runs)

- **PR title must be a Conventional Commit** — the `PR` workflow runs
  `amannn/action-semantic-pull-request`, which fails any PR whose title lacks a type prefix
  (`feat:`, `fix:`, `docs:`, `chore:`, etc.). Editing the PR title re-triggers the check.
- **`devenv`/Nix-based CI jobs are currently broken independent of any change** — the `E2E Test`,
  `Project tests`, and `Hooks` jobs run inside `devenv shell` and fail during shell evaluation with
  `Failed to get shell attribute from devenv` → `error: git-hooks or pre-commit-hooks input
  required`. This happens before any project code runs and also fails on `main` (e.g. E2E Test).
  It stems from the `rolling` nixpkgs/devenv inputs in `devenv.yaml`; it is **not** fixable by a
  normal code/docs change and fixing it would require editing infra config (out of scope for most
  tasks). Do not thrash trying to make these green.

## Definition of done

- The change addresses the task and nothing more.
- The relevant service builds; the relevant tests run (and you report what you ran + the outcome).
- No secrets, credentials, or infra/deployment files changed unless explicitly requested.
