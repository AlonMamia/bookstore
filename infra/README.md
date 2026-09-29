# Bookstore backend infrastructure (AWS)

Terraform for the backend's AWS infrastructure: ECR, ECS on Fargate, an Application Load
Balancer, VPC networking, and a single-AZ RDS Postgres instance. `pp` and `prod` share the
VPC, ALB, NAT gateway, and RDS instance, but get their own ECS service, task definition,
target group, log group, and GitHub OIDC deploy role. The frontend (`bookstore-new`) is not
part of this stack — see "Frontend integration" at the bottom for what it will need.

**Nothing here has been applied.** This is reviewable code; no AWS resources exist yet.

## Cost estimate

us-east-1, both environments running one small task each, month-to-month:

| Item | Est. $/mo | Why it's billed continuously |
|---|---|---|
| NAT Gateway (1, single-AZ) | ~33 | ECS tasks in private subnets need egress for ECR pulls/CloudWatch logs |
| Application Load Balancer | ~16–20 | Single internet-facing entry point for both envs (and the frontend later) |
| Fargate (pp + prod, 0.25 vCPU / 0.5GB each) | ~18 | One always-on task per environment |
| RDS db.t4g.micro Single-AZ + 20GB gp3 + backups | ~14 | Smallest burstable Postgres class |
| Public IPv4 (ALB ENIs + NAT EIP) | ~11 | AWS's 2024 per-IPv4-hour charge — easy to miss |
| CloudWatch Logs | ~1–2 | App logs, 14-day retention |
| ECR storage | ~0.10 | A handful of small tagged images |
| Secrets Manager (1 RDS-managed secret) | ~0.40 | Master credential storage/rotation |
| Data transfer out | ~0.50 | Light traffic assumption |
| **Total** | **~$95–110/mo** | |

**Cost-saving levers that don't expose RDS publicly:**
- Scale the `pp` service to `desired_count = 0` when you're not actively testing against it
  (`terraform apply -var desired_count=0` or edit the task count directly in the console for
  a quick toggle) — saves ~$9/mo per idle stretch; NAT/ALB/RDS keep running since they're shared.
- Use a Fargate Spot capacity provider for `pp` only (not prod) — ~70% cheaper compute for an
  environment that can tolerate interruption. Not implemented here; a reasonable follow-up.
- Replace the NAT gateway with VPC endpoints (S3 gateway [free] + ECR api/dkr + logs interface
  endpoints) if egress ever needs to be locked down to AWS APIs only. Modest savings at this
  traffic level, bigger at higher volume.
- **Don't** make RDS publicly accessible to save on networking — that doesn't even save money
  here (NAT cost is about ECS egress, not RDS) and removes the one hard privacy guarantee.

## Prerequisites

- Terraform >= 1.7, AWS CLI v2.
- An AWS account/region (`us-east-1` by default) you're allowed to create billable resources in.
- Nothing else pre-created — the GitHub OIDC provider, IAM roles, VPC, etc. are all provisioned
  by this config (see `create_github_oidc_provider` in `variables.tf` if your AWS account
  already has a GitHub OIDC provider from another project — AWS allows only one per URL).

## Terraform state setup (one-time, manual — not managed by this config)

Terraform's own state can't bootstrap the bucket it's stored in. Create these once, by hand:

```bash
aws s3api create-bucket --bucket <your-unique-bucket-name> --region us-east-1
aws s3api put-bucket-versioning --bucket <your-unique-bucket-name> \
  --versioning-configuration Status=Enabled
aws s3api put-bucket-encryption --bucket <your-unique-bucket-name> \
  --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'

aws dynamodb create-table --table-name <your-lock-table-name> \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST
```

Then copy `backend.tf.example` to `backend.tf`, fill in the bucket/table names, and run
`terraform init`.

## Local validation (no AWS credentials required)

```bash
cd infra
terraform fmt -check
terraform validate
```

`terraform plan`/`apply` need real AWS credentials and the state backend above — not run as
part of this task.

## GitHub Environments and required values

Create two GitHub Environments named exactly `pp` and `production` (matching what the
workflows in `.github/workflows/` reference). Add these as **Environment variables** (not
secrets — none of them are secret; the IAM trust policy, not secrecy of the ARN, is what
protects the deploy role):

| Variable | Value | Source |
|---|---|---|
| `AWS_REGION` | e.g. `us-east-1` | `var.aws_region` |
| `AWS_DEPLOY_ROLE_ARN` | per-environment | `pp_deploy_role_arn` / `prod_deploy_role_arn` output |
| `ECR_REPOSITORY` | e.g. `bookstore-backend` | `var.ecr_repository_name` |
| `ECS_CLUSTER` | e.g. `bookstore` | `ecs_cluster_name` output |
| `ECS_SERVICE` | `bookstore-pp` / `bookstore-prod` | `pp_service_name` / `prod_service_name` output |
| `ECS_TASK_FAMILY` | `bookstore-pp` / `bookstore-prod` | `pp_task_family` / `prod_task_family` output |

Optionally add required reviewers on the `production` environment for an extra manual gate
before prod deploys run (native GitHub feature, not part of this Terraform).

## Initial deployment sequence

1. Bootstrap the state backend and run `terraform init` (above).
2. `terraform fmt -check && terraform validate`.
3. Copy `terraform.tfvars.example` to `terraform.tfvars`, fill in `github_org`/`github_repo`
   and anything else you want to change, then `terraform plan` and review it carefully.
4. `terraform apply`. The ECS services will come up with a placeholder `:bootstrap` image tag
   that doesn't exist in ECR yet, so tasks will fail to start — that's expected.
5. Read the outputs (`terraform output`) and populate the GitHub Environment variables above.
6. Push to `pp` (or `main`). The `build-and-test` job runs CI; on success the `deploy` job
   builds the real image, pushes it to ECR tagged with the commit SHA, registers a new task
   definition revision pointing at it, updates the ECS service, and waits for the deployment
   to stabilize (`wait-for-service-stability: true` — this is the "wait for it to become
   healthy" step, backed by the ALB target group health check on `/api/actuator/health` and
   the ECS deployment circuit breaker with automatic rollback).
7. Once you own a domain: request/validate an ACM certificate, set `certificate_arn` and the
   two `*_hostname` variables to match it, `terraform apply` again (adds the HTTPS listener +
   HTTP→HTTPS redirect with no other changes), and point your DNS at the `alb_dns_name` output.

## Rollback

Every push builds an immutable, commit-SHA-tagged image that stays in ECR (lifecycle policy
keeps the most recent 30). To roll back:

- **Via GitHub Actions (recommended):** run the `pp.yml`/`prod.yml` workflow manually
  (`workflow_dispatch`) on the target branch with the `image_tag` input set to the previous
  known-good commit SHA. This skips the rebuild, verifies that tag still exists in ECR,
  registers a task definition revision pointing at it, and redeploys with the same
  wait-for-stability/circuit-breaker safety net as a normal deploy.
- **Via AWS CLI (manual, emergency):**
  ```bash
  aws ecs update-service \
    --cluster <ecs_cluster_name> \
    --service bookstore-prod \
    --task-definition bookstore-prod:<previous-revision-number> \
    --force-new-deployment
  ```
  Find `<previous-revision-number>` with `aws ecs list-task-definitions --family-prefix bookstore-prod`.
- The ECS deployment circuit breaker (`deployment_circuit_breaker { enable = true, rollback =
  true }`) also auto-rolls-back a bad deploy on its own if new tasks fail health checks.

## Updating non-image task-definition settings later

`aws_ecs_task_definition.pp`/`.prod` have `lifecycle { ignore_changes = [container_definitions]
}` so a routine `terraform apply` never clobbers the real image tag CI registers (Terraform's
own container definition always says `:bootstrap`). This means changing CPU/memory/env
vars/secrets in Terraform (e.g. a new `prod_cors_allowed_origins`) needs one extra step to take
effect on the running service:

```bash
terraform apply                                      # updates the resource's other args
terraform apply -replace=aws_ecs_task_definition.prod  # forces a fresh revision to be registered
```

The next CI deploy (`describe-task-definition` + patch image + `register-task-definition`)
builds on top of whatever revision Terraform just registered, so it naturally picks up the
new settings alongside its image update.

## Teardown

1. `terraform apply -var rds_deletion_protection=false` (deletion protection blocks destroy
   by design).
2. `terraform destroy`. Set `rds_skip_final_snapshot=true` beforehand if you don't want a
   final RDS snapshot left behind (small storage cost, otherwise kept indefinitely).

## Frontend integration (later, in `bookstore-new` — not part of this task)

- ALB listener rules currently match **host header + `/api/*`** to the backend target groups,
  at priorities 10/20. Add the frontend's target group/rule(s) at priority **>= 100** with a
  `/*` path (or no path condition) so the more specific `/api/*` rules keep winning — same
  host, same HTTPS origin, no CORS needed once both are live.
- `prod_cors_allowed_origins` / `pp_cors_allowed_origins` are CORS fallbacks for local/preview
  frontend work against the deployed backend before it's behind the same origin; update them
  to the real frontend origin(s) once known.
- The backend intentionally has no opinion on how the frontend is hosted (S3+CloudFront,
  another Fargate service, etc.) — whichever it is, it just needs a target group registered
  against this same ALB (or a CloudFront distribution with this ALB as the `/api/*` origin).

## Notes on secrets handling

- The RDS master password is never set by, or readable from, this Terraform config or its
  state (`manage_master_user_password = true` — AWS generates/stores/rotates it in Secrets
  Manager). ECS task definitions read it at container-start time via the `secrets` block.
- Both environments currently use the master user, isolated from each other only by Postgres
  *schema* (`bookstore_pp` / `bookstore_prod` in the one `bookstore_db` database, selected via
  the JDBC `currentSchema` parameter). A reasonable hardening follow-up: create a
  least-privilege role per schema (e.g. via a one-off `psql` session from a bastion/SSM
  session, or the `cyrilgdn/postgresql` Terraform provider run from something with network
  access to the DB subnets) and point each environment's `secrets` at that role's own Secrets
  Manager secret instead of the master one.
