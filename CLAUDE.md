# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

The AWS Containers Retail Sample — a deliberately over-engineered, polyglot microservices retail
store (product catalog, shopping cart, checkout, orders) used to demonstrate containers on AWS.
**Educational sample only, not for production.** This repo is a fork of
`aws-containers/retail-store-sample-app`.

## Monorepo layout

Managed as an **Nx monorepo**; each deployable component lives under `src/` with its own
`project.json` defining Nx targets. The `service` tag is applied to all five application services.

| Component  | Path             | Language / stack         | Persistence backends        |
|------------|------------------|--------------------------|-----------------------------|
| ui         | `src/ui`         | Java (Spring Boot)       | — (calls the four APIs)     |
| catalog    | `src/catalog`    | Go                       | MySQL / MariaDB             |
| cart       | `src/cart`       | Java                     | DynamoDB (or MongoDB)       |
| orders     | `src/orders`     | Java                     | PostgreSQL, RabbitMQ        |
| checkout   | `src/checkout`   | Node (NestJS/TypeScript) | Redis                       |

The UI is the front end and depends on the four backend APIs. Other `src/` dirs: `app` (the
runnable full-stack compose), `e2e`, `load-generator`, `misc`. Deployment lives under `terraform/`
(EKS/ECS/App Runner) and per-service Helm charts (`chart/`).

## Common commands

Run tasks through Nx (`yarn nx`). Targets are consistent across services: `build`, `test`,
`test:integration`, `lint`, `serve`, `container`.

```bash
yarn install                                    # once, from repo root
yarn nx build ui                                # build a single component
yarn nx test ui                                 # unit tests for one component
yarn nx test:integration catalog               # integration tests for one component
yarn nx lint checkout                           # lint one component
yarn nx run-many -t test --projects=tag:service # run a target across all services
yarn nx container ui                            # build a container image
```

### Per-service notes (what the Nx target actually runs)

- **Java (ui / cart / orders):** `test` runs `./mvnw test -DexcludedGroups=integration`;
  `test:integration` runs `./mvnw test -Dgroups=integration`; `lint` runs
  `./mvnw checkstyle:checkstyle`. Requires a JDK. To run one Maven test directly from the service
  dir: `./mvnw test -Dtest=ClassName#method`.
- **catalog (Go):** `build` is `go build -o dist/main main.go`; the Nx unit `test` is a **no-op
  (`exit 0`)** — real tests are `test:integration` (`go test -v ./test/...`). Run a single Go test
  from `src/catalog`: `go test -run TestName ./...`.
- **checkout (Node):** the Nx unit `test` is a **no-op (`exit 0`)**; meaningful tests are
  `test:integration` (Jest e2e). From `src/checkout`, `yarn install` first, then `yarn build`,
  `yarn lint`, `yarn test`, `yarn test:integration`. Run a single Jest test:
  `yarn test:integration -t "test name"`.

Do not claim tests passed if you only ran a no-op target — report what you actually ran.

## Running the full app locally

The runnable compose lives under `src/app` and **requires a `DB_PASSWORD`** (no default — the
stack fails to start without it):

```bash
DB_PASSWORD='<choose-a-password>' yarn compose:up   # docker compose --project-directory src/app up --build --detach --wait
# UI at http://localhost:8080 ; give it 1-2 min to become healthy
yarn compose:down
```

The root `docker-compose.yaml` is **not** the full runnable app; always use `--project-directory
src/app` (via `yarn compose:up`). Each service also has its own compose file for running it in
isolation (`yarn nx compose:up catalog`).

## Conventions

- **Scope changes tightly.** Touch only the service(s) the task concerns; don't refactor across
  services or edit unrelated code.
- **Match per-service style.** Java follows existing package/Checkstyle conventions (formatted with
  `prettier-plugin-java`); TypeScript uses Prettier (`.prettierrc`) + ESLint; Go uses standard `go`
  tooling.
- **Do not modify infra/deploy/release config unless the task explicitly requires it:**
  `terraform/`, Helm `chart/` dirs, `docker-compose*.yaml`, `.github/`, `release-please-config.json`,
  `renovate.json`.

## Development environment

Configuration for [`devenv`](https://devenv.sh/) is provided (`devenv.nix`); `devenv shell` gives
all language toolchains. Otherwise install per-service toolchains manually. See `DEVELOPER_GUIDE.md`
for Nx usage and container-build/publish details, and `SDE-AGENT.md` for autonomous-agent
instructions and CI expectations.
