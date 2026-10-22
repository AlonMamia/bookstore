output "alb_dns_name" {
  description = "ALB DNS name. Point your domain's CNAME/ALIAS records here once you own one."
  value       = aws_lb.this.dns_name
}

output "aws_region" {
  description = "AWS region used by the pp GitHub Actions environment."
  value       = var.aws_region
}

output "ecr_repository_url" {
  description = "Full ECR repository URL, including the registry hostname."
  value       = aws_ecr_repository.backend.repository_url
}

output "ecr_repository_name" {
  description = "ECR repository name used by the GitHub Actions workflows."
  value       = aws_ecr_repository.backend.name
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "pp_service_name" {
  value = aws_ecs_service.pp.name
}

output "pp_hostname" {
  description = "Host header routed to the pp API by the ALB."
  value       = var.pp_hostname
}

output "pp_target_group_arn" {
  description = "Target group used to check pp task health."
  value       = aws_lb_target_group.pp.arn
}

output "pp_log_group_name" {
  description = "CloudWatch log group for pp tasks."
  value       = aws_cloudwatch_log_group.pp.name
}

output "prod_service_name" {
  value = aws_ecs_service.prod.name
}

output "pp_task_family" {
  value = aws_ecs_task_definition.pp.family
}

output "prod_task_family" {
  value = aws_ecs_task_definition.prod.family
}

output "pp_deploy_role_arn" {
  description = "Set as AWS_DEPLOY_ROLE_ARN in the GitHub 'pp' environment."
  value       = aws_iam_role.deploy_pp.arn
}

output "prod_deploy_role_arn" {
  description = "Set as AWS_DEPLOY_ROLE_ARN in the GitHub 'production' environment."
  value       = aws_iam_role.deploy_prod.arn
}

output "rds_endpoint" {
  description = "RDS instance endpoint (host:port). Not secret; used to build DB_URL."
  value       = aws_db_instance.this.endpoint
}

output "rds_master_secret_arn" {
  description = "Secrets Manager ARN holding the AWS-managed RDS master credentials (for manual psql access via a bastion/SSM if ever needed)."
  value       = aws_db_instance.this.master_user_secret[0].secret_arn
}

output "github_oidc_provider_arn" {
  value = local.github_oidc_provider_arn
}
