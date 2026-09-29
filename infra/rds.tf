resource "aws_db_subnet_group" "this" {
  name       = "${var.project_name}-db-subnets"
  subnet_ids = aws_subnet.db[*].id

  tags = {
    Name = "${var.project_name}-db-subnets"
  }
}

# Single-AZ Postgres instance shared by pp and prod. Both environments connect as the
# master user but target different Postgres *schemas* inside the one database (see
# pp_db_schema/prod_db_schema + the JDBC currentSchema param wired in ecs.tf) - so a bug or
# heavy query in one environment can't corrupt the other's data, only share the instance's
# compute/IO capacity. See infra/README.md for the least-privilege-per-schema follow-up.
resource "aws_db_instance" "this" {
  identifier     = "${var.project_name}-db"
  engine         = "postgres"
  engine_version = var.rds_engine_version

  instance_class        = var.rds_instance_class
  allocated_storage     = var.rds_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true
  max_allocated_storage = var.rds_allocated_storage * 2

  db_name  = var.rds_database_name
  username = var.rds_master_username

  # AWS generates, stores and rotates the master password in Secrets Manager. It is never
  # set by (or readable from) this Terraform configuration or its state.
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  multi_az                = false
  publicly_accessible     = false

  backup_retention_period = var.rds_backup_retention_period
  backup_window           = "03:00-04:00"
  maintenance_window      = "mon:04:30-mon:05:30"

  deletion_protection = var.rds_deletion_protection
  skip_final_snapshot = var.rds_skip_final_snapshot
  final_snapshot_identifier = var.rds_skip_final_snapshot ? null : "${var.project_name}-db-final"

  tags = {
    Name = "${var.project_name}-db"
  }
}
