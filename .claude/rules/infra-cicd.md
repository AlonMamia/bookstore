# Infra & CI/CD rules

Read this before touching `Dockerfile`, `compose.yaml`, `.github/workflows/*`, or
anything in `infra/` (Terraform). `infra/README.md` documents the Terraform deploy
sequence in more detail — this file is the cross-cutting conventions that connect
app/CI/infra, which is where changes most often break something silently.

## Build artifact flow (this is one pipeline across three files)

1. `.github/workflows/{dev,pp,prod}.yml` run `mvn --batch-mode clean verify` exactly
   once per push/PR. That's the single source of truth test/build command — don't
   assume a different command is equivalent.
2. On pp/prod (not on PRs), the resulting jar is staged as `build/app.jar` and uploaded
   as a workflow artifact.
3. `Dockerfile` copies that prebuilt `build/app.jar` — it does not invoke Maven and
   does not build from source. If you change how/where the jar is produced in a
   workflow, the Dockerfile's `COPY build/app.jar app.jar` has to keep matching, and
   vice versa.

## Deploys (pp/prod)

- AWS auth is OIDC-federated (`aws-actions/configure-aws-credentials` with
  `role-to-assume`) — no static access keys. Don't introduce long-lived credentials.
- Image tag defaults to the commit SHA; `workflow_dispatch` accepts an explicit
  `image_tag` input for rollback/redeploy of an existing image. The workflow checks
  whether that tag already exists in ECR and skips rebuilding if so.
- ECS deploy renders a task definition (`amazon-ecs-render-task-definition`) and
  applies it with `wait-for-service-stability: true`. The container name `"backend"` is
  shared state between `infra/ecs.tf` and the workflow's `container-name` input — if
  one changes without the other, the render step fails outright (the action throws
  "Invalid task definition: Could not find container definition with matching name"
  and the job fails), it does not silently target the wrong container or deploy a
  broken image.

## Terraform (`infra/`)

- ECS Fargate + ALB, one shared RDS Postgres instance for pp and prod, isolated by
  Postgres *schema* (`pp_db_schema`/`prod_db_schema`, wired into `DB_SCHEMA` and the
  JDBC `currentSchema` param) rather than separate databases or instances. As
  `infra/rds.tf` itself documents: both environments currently connect as the same
  RDS *master* user, so schema separation here isolates namespaces/tables and resource
  contention (a heavy query in one environment can't corrupt the other's data) — it is
  **not** an authorization boundary, since the master user isn't restricted to its own
  schema. `infra/README.md` already tracks a least-privilege-role-per-schema hardening
  follow-up; don't treat the current state as isolated without that follow-up in place.
- `aws_ecs_task_definition.*` and `aws_ecs_service.*` both set
  `lifecycle { ignore_changes = [...] }` on the image/task definition — this is
  deliberate: CI owns image deploys by registering new task definition revisions, not
  `terraform apply`. Don't "fix" this drift by removing the `ignore_changes` block.
- RDS master credentials are managed by AWS (`manage_master_user_password = true`) and
  injected into the ECS task via Secrets Manager `secrets` entries, never as plain
  Terraform variables or workflow env vars.

## Blast radius

Terraform changes, workflow changes affecting the `pp`/`main` branches, and anything
touching AWS IAM roles or credentials are high blast-radius: explain the change and get
explicit confirmation before running `terraform apply` or pushing to a branch that
triggers a deploy. Never run `terraform apply`/`destroy` or trigger a pp/prod deploy
without being asked to.
