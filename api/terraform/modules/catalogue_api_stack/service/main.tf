locals {
  sidecar_container_name = "nginx"
}

module "task_definition" {
  source = "github.com/wellcomecollection/terraform-aws-ecs-service.git//task_definition/container_with_sidecar?ref=v1.5.2"

  task_name = var.namespace

  cpu    = 1024
  memory = 2048

  app_container_image = var.container_image
  app_container_port  = var.container_port
  app_cpu             = 512
  app_memory          = 1024

  app_env_vars = {
    api_host         = "api.wellcomecollection.org"
    apm_service_name = var.namespace
    logstash_host    = var.logstash_host
  }

  secret_app_env_vars = {
    es_host        = "catalogue/api/es_host"
    es_port        = "catalogue/api/es_port"
    es_protocol    = "catalogue/api/es_protocol"
    es_username    = "catalogue/api/es_username"
    es_password    = "catalogue/api/es_password"
    apm_server_url = "catalogue/api/apm_server_url"
    apm_secret     = "catalogue/api/apm_secret"
  }

  sidecar_container_image = var.nginx_container_image
  sidecar_container_port  = var.nginx_container_port
  sidecar_container_name  = local.sidecar_container_name
  sidecar_cpu             = 512
  sidecar_memory          = 1024

  sidecar_env_vars = {
    APP_HOST = "localhost"
    APP_PORT = var.container_port
  }

  aws_region = "eu-west-1"
}

resource "aws_lb_target_group" "tcp" {
  # Must only contain alphanumerics and hyphens.
  name = replace(var.namespace, "_", "-")

  target_type = "ip"

  protocol = "TCP"
  port     = var.nginx_container_port
  vpc_id   = var.vpc_id

  # The default deregistration delay is 5 minutes, which means that ECS
  # takes around 5–7 mins to fully drain connections to and deregister
  # the old task in the course of its blue/green. deployment of an
  # updated service.  Reducing this parameter to 90s makes deployments faster.
  deregistration_delay = 90

  health_check {
    protocol = "TCP"
  }
}

resource "aws_lb_listener" "tcp" {
  load_balancer_arn = var.lb_arn
  port              = var.nginx_container_port
  protocol          = "TCP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.tcp.arn
  }
}

module "service" {
  source = "github.com/wellcomecollection/terraform-aws-ecs-service.git//service?ref=v1.5.2"

  service_name = var.namespace
  cluster_arn  = var.cluster_arn

  task_definition_arn = module.task_definition.arn

  desired_task_count = var.desired_task_count

  subnets = var.subnets

  namespace_id = var.namespace_id

  security_group_ids = var.security_group_ids

  target_group_arn = aws_lb_target_group.tcp.arn
  container_name   = local.sidecar_container_name
  container_port   = var.nginx_container_port
}
