variable "project_name" {
  description = "Short name used as a prefix for all resource names/tags."
  type        = string
  default     = "bookstore"
}

variable "aws_region" {
  description = "AWS region for all resources."
  type        = string
  default     = "us-east-1"
}

# --- Networking ---------------------------------------------------------

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDRs for the 2 public subnets (ALB + NAT gateway), one per AZ."
  type        = list(string)
  default     = ["10.0.0.0/24", "10.0.1.0/24"]
}

variable "app_subnet_cidrs" {
  description = "CIDRs for the 2 private subnets ECS tasks run in (routed to the NAT gateway), one per AZ."
  type        = list(string)
  default     = ["10.0.10.0/24", "10.0.11.0/24"]
}

variable "db_subnet_cidrs" {
  description = "CIDRs for the 2 private, fully isolated subnets RDS lives in (no NAT/IGW route), one per AZ."
  type        = list(string)
  default     = ["10.0.20.0/24", "10.0.21.0/24"]
}

# --- ALB / routing -------------------------------------------------------

variable "certificate_arn" {
  description = <<-EOT
    ACM certificate ARN for the HTTPS (443) listener. Leave empty ("") to run HTTP-only
    for now (no domain owned yet) - the HTTPS listener resource is skipped entirely when
    this is empty and can be turned on later with no other changes.
  EOT
  type        = string
  default     = ""
}

variable "prod_hostname" {
  description = "Host header routed to the prod backend target group. Replace with your real domain once you own one."
  type        = string
  default     = "api.bookstore.example"
}

variable "pp_hostname" {
  description = "Host header routed to the pp backend target group. Replace with your real domain once you own one."
  type        = string
  default     = "pp-api.bookstore.example"
}

# --- Application / containers --------------------------------------------

variable "container_port" {
  description = "Port the Spring Boot container listens on inside the task (set via SERVER_PORT env var)."
  type        = number
  default     = 8080
}

variable "fargate_cpu" {
  description = "Fargate task CPU units (256 = 0.25 vCPU)."
  type        = string
  default     = "256"
}

variable "fargate_memory" {
  description = "Fargate task memory in MiB."
  type        = string
  default     = "512"
}

variable "desired_count" {
  description = "Desired ECS task count per environment. Set to 0 to stop a service (e.g. pp) when idle to save cost."
  type        = number
  default     = 1
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention for both backend log groups."
  type        = number
  default     = 14
}

variable "prod_cors_allowed_origins" {
  description = "CORS_ALLOWED_ORIGINS value for the prod task. Update once the frontend's prod origin exists."
  type        = string
  default     = "https://bookstore.example"
}

variable "pp_cors_allowed_origins" {
  description = "CORS_ALLOWED_ORIGINS value for the pp task. Update once the frontend's pp origin exists."
  type        = string
  default     = "https://pp.bookstore.example"
}

# --- ECR -------------------------------------------------------------------

variable "ecr_repository_name" {
  description = "Name of the single ECR repository the backend image is pushed to (shared by pp and prod)."
  type        = string
  default     = "bookstore-backend"
}

# --- RDS -------------------------------------------------------------------

variable "rds_engine_version" {
  description = "Postgres engine version. Check `aws rds describe-db-engine-versions --engine postgres` for what's current in your region before applying."
  type        = string
  default     = "16.4"
}

variable "rds_instance_class" {
  description = "RDS instance class. db.t4g.micro is the smallest Graviton burstable class."
  type        = string
  default     = "db.t4g.micro"
}

variable "rds_allocated_storage" {
  description = "RDS allocated storage in GB (gp3)."
  type        = number
  default     = 20
}

variable "rds_backup_retention_period" {
  description = "Automated backup retention, in days."
  type        = number
  default     = 7
}

variable "rds_master_username" {
  description = "RDS master username. The password is AWS-managed (manage_master_user_password) and never set here."
  type        = string
  default     = "bookstore_admin"
}

variable "rds_database_name" {
  description = "Single physical database name shared by both environments; pp/prod are isolated as separate Postgres schemas inside it (see pp_db_schema/prod_db_schema)."
  type        = string
  default     = "bookstore_db"
}

variable "pp_db_schema" {
  description = "Postgres schema Flyway/Hibernate target for the pp environment, via the JDBC currentSchema parameter."
  type        = string
  default     = "bookstore_pp"
}

variable "prod_db_schema" {
  description = "Postgres schema Flyway/Hibernate target for the prod environment, via the JDBC currentSchema parameter."
  type        = string
  default     = "bookstore_prod"
}

variable "rds_deletion_protection" {
  description = "Set to false before `terraform destroy` can remove the RDS instance. Kept true by default to avoid accidental deletion."
  type        = bool
  default     = true
}

variable "rds_skip_final_snapshot" {
  description = "If false (default), destroying the instance takes a final snapshot first (safer, has a small one-time storage cost). Set true for fast/cheap teardown of a throwaway environment."
  type        = bool
  default     = false
}

# --- GitHub OIDC -------------------------------------------------------------

variable "github_org" {
  description = "GitHub org/user that owns the repository, used to scope the OIDC trust policy."
  type        = string
  default     = "AlonMamia"
}

variable "github_repo" {
  description = "GitHub repository name, used to scope the OIDC trust policy."
  type        = string
  default     = "bookstore"
}

variable "create_github_oidc_provider" {
  description = <<-EOT
    Whether to create the GitHub Actions OIDC provider. AWS allows only one provider per
    URL per account - set this to false (and add a data source) if another stack in this
    account already created token.actions.githubusercontent.com.
  EOT
  type        = bool
  default     = true
}
