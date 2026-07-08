# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This is the **AWS Containers Retail Store Sample App** — a polyglot microservices retail demo
managed as an [Nx](https://nx.dev/) monorepo. It is intended for **educational purposes only and
not for production use**.

The application is deliberately over-engineered into multiple decoupled components, each with its
own language, framework, and persistence backend, so it can be run across different container
orchestration technologies (Docker Compose, Kubernetes, etc.).

## Repository structure

Each service lives under `src/`:

| Service  | Path           | Language / Stack           | Build / test tool |
| -------- | -------------- | -------------------------- | ----------------- |
| ui       | `src/ui`       | Java (Spring)              | Maven (`pom.xml`) |
| cart     | `src/cart`     | Java (DynamoDB)            | Maven (`pom.xml`) |
| orders   | `src/orders`   | Java                       | Maven (`pom.xml`) |
| catalog  | `src/catalog`  | Go                         | `go` / `Makefile` |
| checkout | `src/checkout` | Node (NestJS / TypeScript) | yarn + Nx + Jest  |

Other notable directories:

- `src/app` — the runnable full-stack Docker Compose project (see [Running the app](#running-the-app)).
- `src/e2e` — Cypress end-to-end tests.
- `src/load-generator` — load-generation utility.
- `terraform/` — deployment targets for EKS / ECS / App Runner.
- `docs/` — feature and architecture documentation.

## Common commands

The [Nx](https://nx.dev/) build system provides a consistent interface across all projects. Run it
via `yarn nx`. Every application service carries the `service` tag.

```bash
yarn install                                   # install dependencies (run from the repo root)

yarn nx build <service>                         # build a single component
yarn nx test <service>                          # run unit tests for a component
yarn nx test:integration <service>              # run integration tests for a component
yarn nx lint <service>                          # lint a component
yarn nx serve <service>                         # run a component locally on port 8080
yarn nx container <service>                     # build a container image

yarn nx run-many -t test --projects=tag:service # run a target across all services
```

To mirror CI, use `affected` against the base branch:

```bash
yarn nx affected --targets=build --base origin/main --parallel 1
yarn nx affected --targets=lint,test:integration,test --base origin/main
```

### Per-service builds and tests

Prefer running the check for the service you changed:

- **checkout (Node):** from `src/checkout`, run `yarn install`, then `yarn build`, `yarn lint`, or
  `yarn test`. The unit `test` script is currently a no-op (`exit 0`); the meaningful tests are
  `test:integration` (Jest e2e).
- **catalog (Go):** from `src/catalog`, run `go build ./...` and `go test ./...` (or the `Makefile`
  targets if present).
- **ui / cart / orders (Java):** from the service directory, run `mvn -q -B verify` (or `mvn test`)
  when a JDK and Maven are available.

## Running the app

The runnable compose stack lives under `src/app` and requires a database password:

```bash
DB_PASSWORD='<choose-a-password>' yarn compose:up   # docker compose up --build --detach --wait
```

Then open the UI at http://localhost:8080. Tear it down with:

```bash
yarn compose:down
```

The stack brings up the five services plus their backing stores (MySQL/MariaDB, Redis,
DynamoDB-local); give it 1–2 minutes to become healthy. Note that the **root**
`docker-compose.yaml` alone is not the full runnable app — use the `src/app` project above.

## Conventions

- **Scope changes tightly.** Touch only the service(s) a task concerns; do not refactor across
  services or edit unrelated code.
- **Match existing per-service conventions.** Java follows the existing package/style; TypeScript
  is formatted with Prettier (`.prettierrc`) and linted with ESLint.
- **Formatting is enforced.** A Prettier pre-commit hook (via [`devenv`](https://devenv.sh/) /
  git-hooks) runs in CI. Run Prettier before committing so new/changed files stay formatted.
- **Do not modify** deployment or infrastructure unless a task explicitly requires it:
  `terraform/`, Helm charts, `docker-compose*.yaml`, `.github/`, `release-please-config.json`,
  `renovate.json`.
- **Commits and PR titles** must follow the [Conventional Commits](https://www.conventionalcommits.org/)
  format (e.g. `feat:`, `fix:`, `chore:`, `docs:`) — a semantic PR-title check runs in CI.

## CI checks

Pull requests to `main` run (see `.github/workflows/`):

- **Semantic Pull Request** (`pr.yaml`) — the PR title must be a valid Conventional Commit.
- **Hooks** (`pr.yaml`) — `devenv test`, which runs the pre-commit hooks (Prettier, `gofmt`,
  Terraform format/lint, sample-data sync).
- **Project tests** (`pr.yaml`) — `nx affected` build, lint, `test`, `test:integration`, and
  container targets for the projects touched by the change.
- **E2E Test** (`e2e-test.yml`) — Docker Compose and Kubernetes (kind) end-to-end tests.
