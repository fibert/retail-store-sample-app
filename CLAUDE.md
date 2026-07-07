# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

## What this repo is

A polyglot microservices retail sample application, managed as an **Nx monorepo**. It is an
educational sample only (not production) and is a fork of
`aws-containers/retail-store-sample-app`.

Each service lives under `src/`:

| Service    | Path             | Stack                    | Build / test tool |
|------------|------------------|--------------------------|-------------------|
| ui         | `src/ui`         | Java (Spring)            | Maven (`pom.xml`) |
| cart       | `src/cart`       | Java (MongoDB/DynamoDB)  | Maven (`pom.xml`) |
| orders     | `src/orders`     | Java                     | Maven (`pom.xml`) |
| catalog    | `src/catalog`    | Go                       | `go` / `Makefile` |
| checkout   | `src/checkout`   | Node (NestJS/TypeScript) | yarn + Nx + Jest  |

The `ui` service is the web front end and depends on the four API services. Additional supporting
directories: `src/app` (runnable Docker Compose stack), `src/e2e`, `src/load-generator`,
`terraform/` (EKS/ECS/App Runner deploy targets), and per-service Helm charts.

## Building & testing

Prefer running the check for the specific service you changed:

- **checkout (Node):** from `src/checkout`, run `yarn install`, then `yarn build`, `yarn lint`, or
  `yarn test`. Note: the unit `test` script is currently a no-op (`exit 0`); the meaningful tests
  are `yarn test:integration` (Jest e2e). Don't claim tests passed if you only ran the no-op — say
  what you actually ran.
- **catalog (Go):** from `src/catalog`, run `go build ./...` and `go test ./...` (or the `Makefile`
  targets if present).
- **ui / cart / orders (Java):** from the service directory, run `mvn -q -B verify` (or `mvn test`)
  if Maven and a JDK are available. If the toolchain is not installed, report that you could not
  run the Java build rather than assuming success.
- Always run the check that corresponds to the code you changed, and report the actual result.

## Running the app

The full runnable Compose stack lives under `src/app` and requires a database password:

```bash
DB_PASSWORD='<choose-a-password>' \
  docker compose --project-directory src/app up --build --detach --wait --wait-timeout 120
# or: DB_PASSWORD='...' yarn compose:up
```

Then open the UI at http://localhost:8080. Tear down with `yarn compose:down` (or
`docker compose --project-directory src/app down`). `DB_PASSWORD` is mandatory and has no default.
The root `docker-compose.yaml` alone is not the full runnable app — use `--project-directory src/app`.

## Conventions

- **Scope changes tightly to the task.** Touch only the service(s) the task concerns; do not
  refactor across services or edit unrelated code.
- **Match existing conventions per service** — Java code follows the existing package/style;
  TypeScript is formatted with Prettier (`.prettierrc`) and linted with ESLint.
- **Do NOT modify** deployment/infra unless the task explicitly asks: `terraform/`, Helm charts,
  `docker-compose*.yaml`, `.github/`, release config (`release-please-config.json`), `renovate.json`.
- Keep commits focused, with a clear message describing the change.

## Related documentation

- `SDE-AGENT.md` — additional instructions for autonomous agents (also parsed for circuit-breaker limits).
- `DEV-DEPLOYMENT.md` — how to stand up a local dev instance.
- `DEVELOPER_GUIDE.md` and `CONTRIBUTING.md` — general development and contribution guidance.
- `README.md` — project overview and architecture.
