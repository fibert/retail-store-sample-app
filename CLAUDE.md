# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A polyglot microservices retail sample (fork of `aws-containers/retail-store-sample-app`),
deliberately over-engineered into decoupled components in different languages, each with its own
persistence backend. **Educational sample only — not for production.**

## Architecture

The `ui` service is a Spring MVC store-front that renders pages and calls the four backend APIs
over HTTP. It is the only component users hit directly; the backends are internal APIs. Backend
endpoints are configured in `src/ui/src/main/resources/application.yml`
(`retail.ui.endpoints.{catalog,carts,checkout,orders}`).

| Service    | Path             | Language / framework      | Persistence      | Default port |
|------------|------------------|---------------------------|------------------|--------------|
| ui         | `src/ui`         | Java / Spring MVC         | (calls backends) | 8080         |
| catalog    | `src/catalog`    | Go                        | MySQL            | 8081         |
| cart       | `src/cart`       | Java / Spring             | Amazon DynamoDB  | 8082         |
| orders     | `src/orders`     | Java / Spring             | MySQL            | 8083         |
| checkout   | `src/checkout`   | Node / NestJS (TS)        | Redis            | 8085         |

Most services support multiple/in-memory backends selectable via env vars (see each service's
`README.md`, e.g. `RETAIL_CATALOG_PERSISTENCE_PROVIDER=in-memory|mysql` for catalog). The
`catalog` and `ui` services seed their data from the shared `samples/` directory via their
`update-samples` Nx targets.

## Build system: Nx monorepo

Everything is orchestrated by [Nx](https://nx.dev/), invoked as `yarn nx`, which provides a
consistent interface (`build`, `test`, `test:integration`, `lint`, `serve`, `container`) over the
per-language toolchains. Run `yarn install` in the repo root first. Each service also has a native
toolchain (Maven, Go, yarn) you can invoke directly from its directory.

All application services carry the `service` tag; `catalog` and `ui` also carry `sample`.

```bash
yarn nx build catalog                              # one service, one target
yarn nx test ui
yarn nx run-many -t test --projects=tag:service    # a target across all services
```

### What each Nx target maps to (matters because commands differ per language)

- **Java (ui, cart, orders):** `build` = `./mvnw -DskipTests package`; `test` = `./mvnw test
  -DexcludedGroups=integration`; `test:integration` = `./mvnw test -Dgroups=integration`; `lint` =
  `./mvnw checkstyle:checkstyle`. Uses the bundled `./mvnw` wrapper. cart's integration tests need
  dummy AWS creds (Nx target sets `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`).
- **Go (catalog):** `build` = `go build -o dist/main main.go`; **`test` is a no-op (`exit 0`)** —
  the real tests are `test:integration` = `go test -v ./test/...`; `lint` is a no-op.
- **Node (checkout):** from `src/checkout`, `yarn build` (nest build), `yarn lint` (eslint).
  **`yarn test` is a no-op (`exit 0`)** — meaningful tests are `yarn test:integration`
  (`jest --config ./test/jest-e2e.json`, uses testcontainers). Run a single Jest test with
  `yarn test:integration -t "test name"`.

> Because unit `test` is a no-op for catalog and checkout, **report what you actually ran** — don't
> claim tests passed when only the no-op executed.

## Running locally

Each service has its own `docker-compose.yml` beside its source. The composed full app lives in
`src/app`:

```bash
yarn compose:up      # docker compose --project-directory src/app up --build --detach --wait
yarn compose:down
yarn nx compose:up catalog   # a single service + its dependencies
```

Note: the root `docker-compose.yaml` requires a `DB_PASSWORD` env var; prefer the `src/app` compose
above for a working local instance.

## Conventions & guardrails

- **Scope changes to the single service the task concerns.** Do not refactor across services.
- Java follows the existing package layout under `com.amazon.sample`; TypeScript is Prettier-
  formatted (`.prettierrc`) and ESLint-linted. Prettier here also formats Java and XML via plugins.
- **Do not modify deployment/infra unless the task explicitly asks:** `terraform/`, Helm `chart/`
  dirs, `docker-compose*.yaml`, `.github/`, `release-please-config.json`, `renovate.json`.
- All components emit Prometheus metrics and OpenTelemetry OTLP traces; preserve that instrumentation
  when editing request paths.
- Releases are automated by release-please; CHANGELOG.md is generated — don't hand-edit it.
