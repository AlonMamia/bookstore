resource "aws_ecs_cluster" "this" {
  name = var.project_name

  setting {
    name  = "containerInsights"
    value = "disabled"
  }

  tags = {
    Name = var.project_name
  }
}

# --- Task definitions --------------------------------------------------------
#
# The "backend" container name here must match the container-name passed to
# aws-actions/amazon-ecs-render-task-definition in the GitHub Actions deploy jobs.
#
# image is a placeholder on first apply (nothing has been pushed to ECR yet). CI's first
# deploy on a push to pp/main registers a new task definition revision with the real,
# commit-sha-tagged image and updates the service - see infra/README.md's deploy sequence.

locals {
  pp_db_url = "jdbc:postgresql://${aws_db_instance.this.address}:5432/${var.rds_database_name}?currentSchema=${var.pp_db_schema}"
  prod_db_url = "jdbc:postgresql://${aws_db_instance.this.address}:5432/${var.rds_database_name}?currentSchema=${var.prod_db_schema}"
}

resource "aws_ecs_task_definition" "pp" {
  family                   = "${var.project_name}-pp"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                       = var.fargate_cpu
  memory                    = var.fargate_memory
  execution_role_arn        = aws_iam_role.ecs_task_execution.arn
  task_role_arn             = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([
    {
      name      = "backend"
      image     = "${aws_ecr_repository.backend.repository_url}:bootstrap"
      essential = true
      portMappings = [
        { containerPort = var.container_port, protocol = "tcp" }
      ]
      environment = [
        { name = "APP_PROFILE", value = "pp" },
        { name = "SERVER_PORT", value = tostring(var.container_port) },
        { name = "CORS_ALLOWED_ORIGINS", value = var.pp_cors_allowed_origins },
        { name = "DB_URL", value = local.pp_db_url },
        { name = "DB_SCHEMA", value = var.pp_db_schema },
      ]
      secrets = [
        { name = "DB_USERNAME", valueFrom = "${aws_db_instance.this.master_user_secret[0].secret_arn}:username::" },
        { name = "DB_PASSWORD", valueFrom = "${aws_db_instance.this.master_user_secret[0].secret_arn}:password::" },
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.pp.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }
    }
  ])

  lifecycle {
    ignore_changes = [container_definitions]
  }

  tags = {
    Name        = "${var.project_name}-pp"
    Environment = "pp"
  }
}

resource "aws_ecs_task_definition" "prod" {
  family                   = "${var.project_name}-prod"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                       = var.fargate_cpu
  memory                    = var.fargate_memory
  execution_role_arn        = aws_iam_role.ecs_task_execution.arn
  task_role_arn             = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([
    {
      name      = "backend"
      image     = "${aws_ecr_repository.backend.repository_url}:bootstrap"
      essential = true
      portMappings = [
        { containerPort = var.container_port, protocol = "tcp" }
      ]
      environment = [
        { name = "APP_PROFILE", value = "prod" },
        { name = "SERVER_PORT", value = tostring(var.container_port) },
        { name = "CORS_ALLOWED_ORIGINS", value = var.prod_cors_allowed_origins },
        { name = "DB_URL", value = local.prod_db_url },
        { name = "DB_SCHEMA", value = var.prod_db_schema },
      ]
      secrets = [
        { name = "DB_USERNAME", valueFrom = "${aws_db_instance.this.master_user_secret[0].secret_arn}:username::" },
        { name = "DB_PASSWORD", valueFrom = "${aws_db_instance.this.master_user_secret[0].secret_arn}:password::" },
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.prod.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }
    }
  ])

  lifecycle {
    ignore_changes = [container_definitions]
  }

  tags = {
    Name        = "${var.project_name}-prod"
    Environment = "prod"
  }
}

# --- Services ------------------------------------------------------------------

resource "aws_ecs_service" "pp" {
  name            = "${var.project_name}-pp"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.pp.arn
  desired_count   = var.pp_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.app[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.pp.arn
    container_name   = "backend"
    container_port   = var.container_port
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # The task definition's container image is updated by CI (register new revision + update
  # service), not by re-applying Terraform; ignore drift on that attribute here.
  lifecycle {
    ignore_changes = [task_definition]
  }

  depends_on = [
    aws_lb_listener_rule.pp_api_http,
    aws_lb_listener_rule.pp_api_https,
  ]

  tags = {
    Name        = "${var.project_name}-pp"
    Environment = "pp"
  }
}

resource "aws_ecs_service" "prod" {
  name            = "${var.project_name}-prod"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.prod.arn
  desired_count   = var.prod_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.app[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.prod.arn
    container_name   = "backend"
    container_port   = var.container_port
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  lifecycle {
    ignore_changes = [task_definition]
  }

  depends_on = [
    aws_lb_listener_rule.prod_api_http,
    aws_lb_listener_rule.prod_api_https,
  ]

  tags = {
    Name        = "${var.project_name}-prod"
    Environment = "prod"
  }
}
