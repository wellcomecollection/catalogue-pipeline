locals {
  api_container_port = 8888

  suffix         = var.instance != "" ? "-${var.instance}" : ""
  namespaced_env = "${var.namespace}-${var.environment}${local.suffix}"

}

module "service" {
  source = "./service"

  service_name                   = local.namespaced_env
  service_discovery_namespace_id = var.service_discovery_namespace_id

  deployment_service_env  = var.environment
  deployment_service_name = "catalogue-api"

  subnets           = var.subnets
  cluster_arn       = var.cluster_arn
  vpc_id            = var.vpc_id
  load_balancer_arn = var.lb_arn

  container_port = local.api_container_port

  container_image = var.api_container_image

  load_balancer_listener_port = var.listener_port

  desired_task_count = var.desired_task_count

  security_group_ids = [
    var.lb_ingress_sg_id,
    var.egress_security_group_id,
    var.interservice_sg_id,
  ]

  environment = {
    api_host         = "api.wellcomecollection.org"
    apm_service_name = var.namespace
  }

  secrets = {
    es_host        = "catalogue/api/es_host"
    es_port        = "catalogue/api/es_port"
    es_protocol    = "catalogue/api/es_protocol"
    es_username    = "catalogue/api/es_username"
    es_password    = "catalogue/api/es_password"
    apm_server_url = "catalogue/api/apm_server_url"
    apm_secret     = "catalogue/api/apm_secret"
  }
}

resource "aws_cloudwatch_metric_alarm" "server_error" {
  alarm_name                = "catalogue-api-${var.environment}-500x"
  comparison_operator       = "GreaterThanOrEqualToThreshold"
  evaluation_periods        = "1"
  metric_name               = "5XXError"
  namespace                 = "AWS/ApiGateway"
  period                    = "60"
  statistic                 = "Sum"
  threshold                 = "10"
  alarm_description         = "This metric monitors 500s from the Catalogue API (${var.environment})"
  alarm_actions             = [aws_sns_topic.server_error_alarm.arn]
  insufficient_data_actions = []

  dimensions = {
    Stage   = var.environment
    ApiName = "Catalogue API"
  }
}

resource "aws_sns_topic" "server_error_alarm" {
  name = "catalogue-api-${var.environment}-500x"
}
