# --- ECS task execution role (pulls image, writes logs, fetches task-def secrets) --------

data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ecs_task_execution" {
  name               = "${var.project_name}-ecs-task-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution_managed" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "ecs_task_execution_secrets" {
  statement {
    sid       = "ReadRdsMasterSecret"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_db_instance.this.master_user_secret[0].secret_arn]
  }
}

resource "aws_iam_role_policy" "ecs_task_execution_secrets" {
  name   = "${var.project_name}-ecs-task-execution-secrets"
  role   = aws_iam_role.ecs_task_execution.id
  policy = data.aws_iam_policy_document.ecs_task_execution_secrets.json
}

# --- ECS task role (assumed by the running app itself) -----------------------------------
# Empty for now: the backend doesn't call any AWS APIs. Kept as its own role (rather than
# reusing the execution role) so permissions can be added here later without touching the
# execution role's image-pull/secrets/logs access.

resource "aws_iam_role" "ecs_task" {
  name               = "${var.project_name}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

# --- GitHub Actions OIDC ------------------------------------------------------------------

resource "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 1 : 0

  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  # GitHub's OIDC token-signing certificate thumbprints. Verify these are still current
  # against https://github.blog/changelog/ before applying if this has aged.
  thumbprint_list = [
    "6938fd4d98bab03faadb97b34396831e3780aea1",
    "1c58a3a8518e8759bf075b76b750d4f2df264fcd",
  ]
}

locals {
  github_oidc_provider_arn = var.create_github_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : "arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/token.actions.githubusercontent.com"
}

# One deploy role per environment, each trusted for exactly one branch ref and scoped to
# only that environment's ECS service/task-definition family/log group. A compromised pp
# workflow cannot deploy to or read prod's resources, and vice versa.

data "aws_iam_policy_document" "deploy_assume_pp" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values = ["repo:${var.github_org}/${var.github_repo}:environment:pp"]
    }
  }
}

data "aws_iam_policy_document" "deploy_assume_prod" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values = ["repo:${var.github_org}/${var.github_repo}:environment:production"]
    }
  }
}

resource "aws_iam_role" "deploy_pp" {
  name               = "${var.project_name}-pp-deploy"
  assume_role_policy = data.aws_iam_policy_document.deploy_assume_pp.json
}

resource "aws_iam_role" "deploy_prod" {
  name               = "${var.project_name}-prod-deploy"
  assume_role_policy = data.aws_iam_policy_document.deploy_assume_prod.json
}

# RegisterTaskDefinition has no resource-level permission support in IAM (AWS requires "*"
# for this action) - everything else below is scoped as tightly as ECS's IAM model allows.
data "aws_iam_policy_document" "deploy_pp" {
  statement {
    sid       = "EcrAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid    = "EcrPushPull"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:PutImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:BatchGetImage",
      "ecr:DescribeImages",
    ]
    resources = [aws_ecr_repository.backend.arn]
  }

  statement {
    sid       = "EcsRegisterTaskDefinition"
    actions   = ["ecs:RegisterTaskDefinition"]
    resources = ["*"]
  }

  statement {
    sid     = "EcsDescribeTaskDefinition"
    actions = ["ecs:DescribeTaskDefinition"]
    resources = [
      "arn:aws:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:task-definition/${aws_ecs_task_definition.pp.family}:*",
    ]
  }

  statement {
    sid     = "EcsDeploy"
    actions = ["ecs:UpdateService", "ecs:DescribeServices"]
    resources = [
      "arn:aws:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:service/${aws_ecs_cluster.this.name}/${aws_ecs_service.pp.name}",
    ]
  }

  statement {
    sid       = "PassEcsRoles"
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.ecs_task_execution.arn, aws_iam_role.ecs_task.arn]
    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "deploy_prod" {
  statement {
    sid       = "EcrAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid    = "EcrPushPull"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:PutImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:BatchGetImage",
      "ecr:DescribeImages",
    ]
    resources = [aws_ecr_repository.backend.arn]
  }

  statement {
    sid       = "EcsRegisterTaskDefinition"
    actions   = ["ecs:RegisterTaskDefinition"]
    resources = ["*"]
  }

  statement {
    sid     = "EcsDescribeTaskDefinition"
    actions = ["ecs:DescribeTaskDefinition"]
    resources = [
      "arn:aws:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:task-definition/${aws_ecs_task_definition.prod.family}:*",
    ]
  }

  statement {
    sid     = "EcsDeploy"
    actions = ["ecs:UpdateService", "ecs:DescribeServices"]
    resources = [
      "arn:aws:ecs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:service/${aws_ecs_cluster.this.name}/${aws_ecs_service.prod.name}",
    ]
  }

  statement {
    sid       = "PassEcsRoles"
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.ecs_task_execution.arn, aws_iam_role.ecs_task.arn]
    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "deploy_pp" {
  name   = "${var.project_name}-pp-deploy"
  role   = aws_iam_role.deploy_pp.id
  policy = data.aws_iam_policy_document.deploy_pp.json
}

resource "aws_iam_role_policy" "deploy_prod" {
  name   = "${var.project_name}-prod-deploy"
  role   = aws_iam_role.deploy_prod.id
  policy = data.aws_iam_policy_document.deploy_prod.json
}
