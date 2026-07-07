# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

The AWS Containers Retail Sample — a deliberately over-engineered retail store demo
(product catalog, shopping cart, checkout) built as a polyglot microservices monorepo.
It is intended for educational/demo purposes only, not production.

## Monorepo layout & tooling

Components live under `src/` and are orchestrated with the [Nx](https://nx.dev/) build
system. Run Nx via `yarn nx`. Each application component is tagged `service`.

| Component | Path            | Language / Framework      | Persistence options       |
| --------- | --------------- | ------------------------- | ------------------------- |
| ui        | `src/ui`        | Java (Spring Boot, Maven) | — (aggregates the APIs)   |
| catalog   | `src/catalog`   | Go                        | MySQL/MariaDB             |
| cart      | `src/cart`      | Java (Maven)              | DynamoDB                  |
| orders    | `src/orders`    | Java (Maven)              | PostgreSQL, RabbitMQ      |
| checkout  | `src/checkout`  | Node (NestJS/TypeScript)  | Redis                     |

Other `src/` dirs: `app` (compose for the full local stack), `e2e` (Playwright end-to-end
tests), `load-generator`, `misc`.

The UI is the web front end and depends on the four backend APIs. Each service is
independently deployable and owns its own container image and Helm chart (`chart/`).

## Common commands

Nx targets are consistent across components; run them for one project or many:

```bash
yarn nx build <project>              # e.g. yarn nx build ui
yarn nx test <project>               # unit tests
yarn nx test:integration <project>   # integration tests
yarn nx lint <project>
yarn nx serve <project>              # run locally on port 8080
yarn nx container <project>          # build container image

# Run a target across all application services at once:
yarn nx run-many -t test --projects=tag:service
```

The per-component target definitions (in each `src/<component>/project.json`) map to the
native toolchains — knowing these matters because unit `test` is a no-op for some services:

- **ui / cart / orders (Java):** build = `./mvnw -DskipTests package`; `test` runs
  `./mvnw test -DexcludedGroups=integration`; `test:integration` runs
  `./mvnw test -Dgroups=integration`; lint = `./mvnw checkstyle:checkstyle`. Uses the
  bundled `./mvnw` wrapper. Run a single Java test with
  `./mvnw test -Dtest=ClassName#method` from the component dir.
- **catalog (Go):** build = `go build`; **unit `test` is `exit 0`** — the real tests are
  `test:integration` (`go test -v ./test/...`). Run a single Go test with
  `go test -run TestName ./...`.
- **checkout (Node):** `install` runs `yarn install`; `test:integration` is the meaningful
  Jest e2e suite. Run a single Jest test with `yarn test -t "test name"` from `src/checkout`.

## Running the full app locally

The runnable Docker Compose stack lives under `src/app` and **requires a DB password**
(no default — the stack won't start without it):

```bash
DB_PASSWORD='<password>' yarn compose:up      # docker compose --project-directory src/app up --build --wait
yarn compose:down
```

The UI is then at http://localhost:8080; allow ~1–2 min for services to become healthy.
Individual components also have their own `docker-compose.yml` alongside their source;
run one with its dependencies via `yarn nx compose:up <project>`.

## CI expectations

- **Pull request titles must be [Conventional Commits](https://www.conventionalcommits.org/)**
  (e.g. `feat:`, `fix:`, `docs:`) — the `Semantic Pull Request` check enforces this, and
  release-please derives the changelog from it.
- CI (`.github/workflows/pr.yaml`) runs `yarn nx affected` for `build`, `lint`, `test`,
  `test:integration`, and `container` against `origin/main`, so only components touched by
  your change are built and tested.
- Java code follows the existing package layout and Checkstyle rules; TypeScript is
  formatted with Prettier (`.prettierrc`) and linted with ESLint.

## Conventions

- Scope changes tightly to the component(s) the task concerns; do not refactor across
  services or reformat unrelated code.
- Deployment/infra lives in `terraform/` (EKS, ECS, App Runner) and per-component `chart/`
  Helm charts — out of scope unless a task explicitly targets them.
