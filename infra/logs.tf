resource "aws_cloudwatch_log_group" "pp" {
  name              = "/ecs/${var.project_name}-pp"
  retention_in_days = var.log_retention_days

  tags = {
    Name        = "${var.project_name}-pp-logs"
    Environment = "pp"
  }
}

resource "aws_cloudwatch_log_group" "prod" {
  name              = "/ecs/${var.project_name}-prod"
  retention_in_days = var.log_retention_days

  tags = {
    Name        = "${var.project_name}-prod-logs"
    Environment = "prod"
  }
}
