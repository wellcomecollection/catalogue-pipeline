locals {
  namespaced_env = "${var.namespace}-${var.environment}"
}

module "service" {
  source = "./service"

  namespace    = "${local.namespaced_env}"
  namespace_id = "${aws_service_discovery_private_dns_namespace.namespace.id}"

  subnets      = ["${var.subnets}"]
  cluster_name = "${var.cluster_name}"
  vpc_id       = "${var.vpc_id}"
  lb_arn       = "${var.lb_arn}"

  container_port = "${local.api_container_port}"

  container_image = "${var.api_container_image}"
  es_config       = "${var.es_config}"
  listener_port   = "${var.listener_port}"

  nginx_container_image = "${var.nginx_container_image}"
  nginx_container_port  = "${local.nginx_container_port}"

  task_desired_count = "${var.task_desired_count}"

  security_group_ids = ["${var.lb_ingress_sg_id}"]

  service_egress_security_group_id = "${aws_security_group.service_egress_security_group.id}"
  interservice_security_group_id   = "${var.interservice_sg_id}"

  logstash_host = "${var.logstash_host}"
}

resource "aws_service_discovery_private_dns_namespace" "namespace" {
  name = "${var.namespace}-${var.environment}"
  vpc  = "${var.vpc_id}"
}

resource "aws_security_group" "service_egress_security_group" {
  name        = "${var.namespace}-${var.environment}-service_egress_security_group"
  description = "Allow any traffic on any port out of the (${var.namespace}-${var.environment}) service"
  vpc_id      = "${var.vpc_id}"

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags {
    Name = "${var.namespace}"
  }
}
