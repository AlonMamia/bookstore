output "alb_dns_name" {
  description = "ALB DNS name. Point your domain's CNAME/ALIAS records here once you own one."
  value       = aws_lb.this.dns_name
}

output "ecr_repository_url" {
  description = "ECR repository URL. Set as the ECR_REPOSITORY GitHub Environment variable (repo name only, not the full URL, is also fine - see README)."
  value       = aws_ecr_repository.backend.repository_url
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "pp_service_name" {
  value = aws_ecs_service.pp.name
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
