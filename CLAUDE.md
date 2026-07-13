# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This is the AWS Containers Retail Sample — a deliberately over-engineered sample retail store
application used to illustrate concepts around running containers on AWS. It is a monorepo of
decoupled services (product catalog, shopping cart, checkout, orders) fronted by a web UI, each
with its own language, container image, and Helm chart. It is intended for educational/demo use
only, not production.

See `README.md` for the full feature list and `DEVELOPER_GUIDE.md` for deeper workflow details.

## Repository layout

- `src/ui/` — Store front end (Java, Spring Boot)
- `src/catalog/` — Product catalog API (Go)
- `src/cart/` — Shopping cart API (Java)
- `src/orders/` — Orders API (Java)
- `src/checkout/` — Checkout orchestration API (Node / NestJS)
- `src/app/` — Compose/Tilt/Helmfile definitions to run the whole app together
- `src/e2e/` — Cypress end-to-end tests
- `src/load-generator/`, `src/recommendations/`, `src/misc/` — Supporting components
- `terraform/` — Deployment targets (EKS/ECS/App Runner)
- `docs/` — Documentation and diagrams

Each service directory contains its own `README.md`, `Dockerfile`, `docker-compose.yml`, and
`chart/` (Helm), plus a `project.json` that wires it into the nx build graph.

## Tooling

- **Package manager:** Yarn 4 (`yarn install` at the root).
- **Build system:** [nx](https://nx.dev/) — run via `yarn nx`. The `service` tag is applied to all
  application services.
- **Runtime versions:** managed by [mise](https://mise.jdx.dev/) (`.mise.toml`). Run `mise install`
  to provision Java 21, Node 22, Go 1.25, etc.
- **Git hooks:** [lefthook](https://lefthook.dev/) (`lefthook.yml`), installed via the `prepare`
  script on `yarn install`.

## Common commands

Run these from the repository root.

```bash
# Per-component tasks (replace <component>, e.g. ui, catalog, cart, orders, checkout)
yarn nx build <component>              # Build a component
yarn nx test <component>               # Run unit tests
yarn nx test:integration <component>   # Run integration tests
yarn nx lint <component>               # Lint
yarn nx serve <component>              # Serve locally on port 8080
yarn nx container <component>          # Build the container image

# Across all application services
yarn nx run-many -t test --projects=tag:service
yarn nx run-many -t container --projects=tag:service

# Only what changed relative to main (mirrors CI)
yarn nx affected --targets=build --base origin/main
yarn nx affected --targets=lint,test:integration,test --base origin/main
```

## Running the full application locally

The runnable compose stack lives under `src/app` and requires a database password:

```bash
DB_PASSWORD='<choose-a-password>' yarn compose:up   # or: docker compose --project-directory src/app up --build --detach --wait
yarn compose:down                                   # tear down
```

The UI is then available at http://localhost:8080. `DB_PASSWORD` is mandatory and has no default.
Note that the root `docker-compose.yaml` alone is not the full runnable app. See
`DEV-DEPLOYMENT.md` for details.

## Conventions

- **Scope changes tightly.** Touch only the service(s) a change concerns; do not refactor across
  services or reformat unrelated code.
- **Match each service's existing style.** Java follows the existing package layout; Go is
  formatted with `gofmt`; TypeScript/JavaScript, JSON, Markdown, YAML, and XML are formatted with
  Prettier (`.prettierrc`); Terraform with `terraform fmt`. The `pre-commit` lefthook applies these
  automatically on commit.
- **Do not modify infra/deploy/release config** unless the task explicitly requires it:
  `terraform/`, Helm charts, `docker-compose*.yaml`, `.github/`, `release-please-config.json`,
  `renovate.json`.

## Pull requests and CI

- **PR titles must be Conventional Commits** (e.g. `feat: ...`, `fix: ...`, `docs: ...`). This is
  enforced by the "Semantic Pull Request" check and drives release-please changelog generation.
- CI (`.github/workflows/pr.yaml`) runs three jobs on every PR to `main`:
  1. **Semantic Pull Request** — validates the PR title.
  2. **Hooks** — runs `yarn prettier --check` (and other formatters) on changed files via lefthook.
  3. **Project tests** — `yarn nx affected` build, lint, `test`, `test:integration`, and container
     builds for affected projects.
- Before opening a PR: run the affected tests/lint locally with the same commands as CI, and ensure
  files are formatted (`yarn prettier --check .`).

## Additional context files

- `CONTRIBUTING.md` — contribution process.
- `SDE-AGENT.md` — instructions and circuit-breaker limits for autonomous agents.
- `DEV-DEPLOYMENT.md` — local dev deployment details.
- `DEVELOPER_GUIDE.md` — nx usage, container image building/publishing, and compose workflows.
