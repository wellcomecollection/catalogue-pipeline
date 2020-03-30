locals {
  namespaced_env = "${var.namespace}-${var.environment}"
}

module "service" {
  source = "./service"

  namespace    = local.namespaced_env
  namespace_id = aws_service_discovery_private_dns_namespace.namespace.id

  subnets     = var.subnets
  cluster_arn = var.cluster_arn
  vpc_id      = var.vpc_id
  lb_arn      = var.lb_arn

  container_port = local.api_container_port

  container_image = local.api_container_image
  listener_port   = var.listener_port

  nginx_container_image = local.nginx_container_image
  nginx_container_port  = local.nginx_container_port

  desired_task_count = var.desired_task_count

  security_group_ids = [
    var.lb_ingress_sg_id,
    var.egress_security_group_id,
    var.interservice_sg_id,
  ]

  logstash_host = var.logstash_host
}

resource "aws_service_discovery_private_dns_namespace" "namespace" {
  name = "${var.namespace}-${var.environment}"
  vpc  = var.vpc_id
}
