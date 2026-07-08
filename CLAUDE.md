# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This is the AWS Containers Retail Sample App — a sample retail store application (product catalog,
shopping cart, checkout) intended for educational purposes only, not production use. It is
deliberately over-engineered into multiple decoupled services written in different languages, with
several persistence backends and support for multiple orchestrators (Docker Compose, Kubernetes).

## Repository structure

This is an [nx](https://nx.dev/) monorepo. Each application service lives under `src/`:

- `src/ui` — web front end (Java / Spring Boot)
- `src/catalog` — product catalog (Go)
- `src/cart` — shopping cart (Java / Spring Boot)
- `src/orders` — orders (Java / Spring Boot)
- `src/checkout` — checkout (Node.js / NestJS)

Other top-level directories: `terraform/` (deploy targets for EKS/ECS/App Runner), `docs/`,
`samples/`, `scripts/`, and `oss/`.

## Toolchain

Tool versions are pinned in `.mise.toml` (Java 21, Node 22, Go 1.25, Maven 3.9, Terraform 1.14,
etc.). Install them with `mise install`, or install manually. The package manager is Yarn 4
(via Corepack). Run `yarn install` in the root first.

## Common commands

Tasks run through nx via `yarn nx`. See `DEVELOPER_GUIDE.md` for more detail.

- `yarn nx build <project>` — build a component (e.g. `yarn nx build ui`)
- `yarn nx test <project>` — run unit tests
- `yarn nx test:integration <project>` — run integration tests
- `yarn nx lint <project>` — lint a component
- `yarn nx serve <project>` — run a component locally (port 8080)
- `yarn nx container <project>` — build a container image

Run a task across all services with the `service` tag:

```
yarn nx run-many -t test --projects=tag:service
```

## Running locally with Docker Compose

The full stack requires a database password and runs from `src/app`:

```
DB_PASSWORD='<choose-a-password>' yarn compose:up   # UI at http://localhost:8080
yarn compose:down
```

Individual components have their own compose files and can be run with
`yarn nx compose:up <project>`. See `DEV-DEPLOYMENT.md` for details.

## Conventions

- **Match existing conventions per service.** Java follows the existing package/style; Go is
  formatted with `gofmt`; TypeScript is formatted with Prettier and linted with ESLint;
  Terraform with `terraform fmt`.
- Formatting is enforced by [lefthook](https://lefthook.dev/) pre-commit hooks (`lefthook.yml`)
  and re-checked in CI. Prettier config is in `.prettierrc` (with Java and XML plugins);
  ignore rules in `.prettierignore`.
- Keep changes focused and scoped to the service the task concerns; avoid refactoring unrelated
  code across services.

## CI

CI runs on pull requests via `.github/workflows/`:

- **Semantic Pull Request** — the PR title must follow
  [Conventional Commits](https://www.conventionalcommits.org/) (e.g. `feat:`, `fix:`, `docs:`).
- **Hooks** — runs `prettier --check` (plus `gofmt`, `terraform fmt`, `tflint`) on changed files.
- **Project tests** — `yarn nx affected` runs `build`, `lint`, `test`, `test:integration`, and
  `container` for affected projects.
- **E2E Test** — Cypress end-to-end tests against the composed stack.
