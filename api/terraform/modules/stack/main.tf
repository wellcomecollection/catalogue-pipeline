locals {
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

  container_image = local.api_container_image

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
