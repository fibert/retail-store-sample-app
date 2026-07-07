# Dev deployment — retail-store-sample-app

How to stand up a local dev instance of the app. (The SDE Agent reads this for context; today it
only auto-runs the root `docker-compose.yaml`, so for anything more, follow the steps below.)

## Full stack via Docker Compose

The runnable compose lives under `src/app` and requires a database password:

```bash
DB_PASSWORD='<choose-a-password>' \
  docker compose --project-directory src/app up --build --detach --wait --wait-timeout 120
# or: DB_PASSWORD='...' yarn compose:up
```

Then open the UI at http://localhost:8080. Tear down with:

```bash
docker compose --project-directory src/app down     # or: yarn compose:down
```

## Services

Brought up by the compose stack: **ui** (Java, the web front end), **catalog** (Go), **cart**
(Java), **orders** (Java), **checkout** (Node/NestJS), plus their backing stores (MySQL/MariaDB,
Redis, DynamoDB-local). The UI depends on the four APIs; give the stack ~1–2 min to become healthy.

## Notes

- Requires Docker with the compose v2 plugin, run in privileged mode (the SDE Agent build is).
- `DB_PASSWORD` is mandatory and has no default — the stack fails to start without it.
- The root `docker-compose.yaml` alone is not the full runnable app; use `--project-directory src/app`.
- Deploy targets for EKS/ECS/App Runner live under `terraform/` — out of scope for local dev.
