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
- Both ECS services initially have zero tasks (`pp_desired_count = 0` and
  `prod_desired_count = 0`). After deploying a real image, increase only `pp_desired_count`
  to `1` when you're ready to run the pp service; leave `prod_desired_count` at `0` until
  prod is ready to run. Reducing `pp_desired_count` back to `0` while pp is idle saves
  ~$9/mo; NAT/ALB/RDS keep running since they're shared.
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

## First deployment: pp only

This procedure provisions the shared infrastructure while leaving both ECS services stopped,
deploys a real image to pp through the existing GitHub Actions workflow, and then starts only
pp. It does not deploy or activate production.

1. Bootstrap the Terraform state backend as described above, copy the example variables, and
   initialize Terraform:

   ```bash
   cd infra
   cp terraform.tfvars.example terraform.tfvars
   ```

   Confirm `pp_desired_count = 0` and `prod_desired_count = 0` in `terraform.tfvars`; keep both
   values at zero for the initial apply. Set `github_org` and `github_repo` to the repository
   used by the workflows, then run:

   ```bash
   terraform init
   terraform fmt -check
   terraform validate
   terraform plan -var='pp_desired_count=0' -var='prod_desired_count=0' -out=first-deploy.tfplan
   terraform show first-deploy.tfplan
   terraform apply first-deploy.tfplan
   ```

   Review the plan before applying it. With both desired counts at zero, the placeholder
   `:bootstrap` image is not started.

2. Create the GitHub Environment named exactly `pp` (do not configure or deploy the
   `production` environment as part of this procedure). On the `pp` environment, add a
   **deployment branch restriction** limiting deployments to the `pp` branch only (GitHub
   Environments → `pp` → "Deployment branches and tags" → "Selected branches and tags" →
   add `pp`). When `production` is configured later, restrict it to the `main` branch only,
   the same way. This backs the OIDC trust policy's `environment:` subject condition with a
   GitHub-side control so a workflow run against the wrong branch cannot even reach the
   environment's secrets/variables or request an OIDC token under it.

   Set these **Environment variables** from the Terraform outputs; none are secrets:

   | GitHub `pp` variable | Terraform output |
   |---|---|
   | `AWS_REGION` | `aws_region` |
   | `AWS_DEPLOY_ROLE_ARN` | `pp_deploy_role_arn` |
   | `ECR_REPOSITORY` | `ecr_repository_name` |
   | `ECS_CLUSTER` | `ecs_cluster_name` |
   | `ECS_SERVICE` | `pp_service_name` |
   | `ECS_TASK_FAMILY` | `pp_task_family` |

   From the `infra` directory, the GitHub CLI commands are:

   ```bash
   gh variable set AWS_REGION --env pp --body "$(terraform output -raw aws_region)"
   gh variable set AWS_DEPLOY_ROLE_ARN --env pp --body "$(terraform output -raw pp_deploy_role_arn)"
   gh variable set ECR_REPOSITORY --env pp --body "$(terraform output -raw ecr_repository_name)"
   gh variable set ECS_CLUSTER --env pp --body "$(terraform output -raw ecs_cluster_name)"
   gh variable set ECS_SERVICE --env pp --body "$(terraform output -raw pp_service_name)"
   gh variable set ECS_TASK_FAMILY --env pp --body "$(terraform output -raw pp_task_family)"
   gh variable list --env pp
   ```

3. Run the existing pp workflow against the `pp` branch, leaving its optional `image_tag`
   input empty so the workflow uses that commit's SHA. Either push the intended application
   commit to `pp`, or dispatch the workflow manually:

   ```bash
   gh workflow run pp.yml --ref pp
   gh run list --workflow pp.yml --branch pp
   gh run watch <run-id> --exit-status
   ```

   Confirm the successful run built and pushed the commit-SHA-tagged image, registered a new
   task-definition revision, and updated the pp service. On a first deployment the image tag
   should be absent, so the workflow builds and pushes it. If retrying a commit whose image
   already exists, this workflow intentionally reuses that image instead of pushing it again.
   The workflow is configured for `pp` pushes and manual dispatches on `pp`; it does not
   deploy from pull requests or other manual-dispatch refs.

   **A successful workflow while `desired_count` is zero does not prove that the application
   works.** ECS can register the revision and report the service stable with no running tasks.
   Record the task-definition ARN/revision and image tag selected by the service now; these
   are the values to verify again after scaling:

   ```bash
   PP_CLUSTER="$(terraform output -raw ecs_cluster_name)"
   PP_SERVICE="$(terraform output -raw pp_service_name)"
   PP_DEPLOYED_TD="$(aws ecs describe-services --cluster "$PP_CLUSTER" --services "$PP_SERVICE" \
     --query 'services[0].taskDefinition' --output text)"
   printf 'Deployed pp task definition: %s\n' "$PP_DEPLOYED_TD"
   aws ecs describe-task-definition --task-definition "$PP_DEPLOYED_TD" \
     --query 'taskDefinition.{revision:revision,image:containerDefinitions[0].image}' --output table
   ```

4. Only after the workflow has deployed the real image, edit `terraform.tfvars` to set
   `pp_desired_count = 1` and keep `prod_desired_count = 0`. Plan, review, and apply that
   change:

   ```bash
   terraform plan -var='pp_desired_count=1' -var='prod_desired_count=0' -out=pp-scale.tfplan
   terraform show pp-scale.tfplan
   terraform apply pp-scale.tfplan
   ```

   Keep those values in `terraform.tfvars` so later Terraform runs do not scale pp back down.

5. Verify the live pp deployment after scaling:

   ```bash
   PP_CLUSTER="$(terraform output -raw ecs_cluster_name)"
   PP_SERVICE="$(terraform output -raw pp_service_name)"
   PP_TD="$(aws ecs describe-services --cluster "$PP_CLUSTER" --services "$PP_SERVICE" \
     --query 'services[0].taskDefinition' --output text)"
   printf 'Current service task definition: %s\n' "$PP_TD"
   aws ecs describe-task-definition --task-definition "$PP_TD" \
     --query 'taskDefinition.{revision:revision,image:containerDefinitions[0].image}' --output table
   aws ecs wait services-stable --cluster "$PP_CLUSTER" --services "$PP_SERVICE"
   PP_TASK="$(aws ecs list-tasks --cluster "$PP_CLUSTER" --service-name "$PP_SERVICE" \
     --desired-status RUNNING --query 'taskArns[0]' --output text)"
   aws ecs describe-tasks --cluster "$PP_CLUSTER" --tasks "$PP_TASK" \
     --query 'tasks[0].{taskDefinition:taskDefinitionArn,containers:containers[].{name:name,image:image,imageDigest:imageDigest,lastStatus:lastStatus}}' \
     --output table
   aws elbv2 describe-target-health \
     --target-group-arn "$(terraform output -raw pp_target_group_arn)" \
     --query 'TargetHealthDescriptions[].{state:TargetHealth.State,reason:TargetHealth.Reason}' \
     --output table
   ```

   The task-definition ARN/revision must match the one recorded from the GitHub Actions
   deployment, and its `backend` image must be the ECR image tagged with that workflow
   commit's SHA—not `:bootstrap`. Confirm the running task's image and digest match that
   revision. The pp target's state must become `healthy`.

   Check the health endpoint through the configured pp host header. In the default
   HTTP-only/no-DNS setup, `--connect-to` sends the configured hostname to the ALB while
   preserving that hostname for ALB routing:

   ```bash
   PP_HOST="$(terraform output -raw pp_hostname)"
   ALB_DNS="$(terraform output -raw alb_dns_name)"
   curl --fail --show-error --connect-to "${PP_HOST}:80:${ALB_DNS}:80" \
     "http://${PP_HOST}/api/actuator/health"
   ```

   If DNS is configured, use:

   ```bash
   curl --fail --show-error "http://${PP_HOST}/api/actuator/health"
   ```

   If HTTPS is enabled, test `https://${PP_HOST}/api/actuator/health` through the configured
   DNS/ACM hostname.

   Finally, inspect the pp task logs and confirm Spring Boot startup and Flyway/database
   migration messages are present:

   ```bash
   aws logs tail "$(terraform output -raw pp_log_group_name)" --since 30m
   ```

   These runtime checks—not workflow success at zero tasks—establish that the app started,
   migrations ran, the ALB target is healthy, and the health endpoint responds.

## Terraform and pipeline task-definition ownership

The Terraform task definitions ignore changes to `container_definitions`, and each ECS
service ignores changes to `task_definition`. Therefore the pp workflow's registered
task-definition revision and service selection are preserved when the later Terraform apply
changes only `pp_desired_count`; Terraform will not replace the pipeline's real image with
the initial `:bootstrap` definition. Production has the same lifecycle protection but remains
at `prod_desired_count = 0` throughout this procedure.

Once you own a domain, request/validate an ACM certificate, set `certificate_arn` and the
`*_hostname` variables to match it, apply Terraform to add HTTPS + the HTTP-to-HTTPS redirect,
and point DNS at the `alb_dns_name` output. This is outside the first pp deployment procedure.

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
