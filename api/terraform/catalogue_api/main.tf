module "service" {
  source = "service"

  namespace    = "${var.namespace}-${var.environment}"
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
}

resource "aws_service_discovery_private_dns_namespace" "namespace" {
  name = "${var.namespace}-${var.environment}"
  vpc  = "${var.vpc_id}"
}

module "gateway_stage" {
  source = "git::https://github.com/wellcometrust/terraform.git//api_gateway/modules/stage?ref=v17.0.0"

  stage_name = "${var.environment}"
  api_id     = "${var.api_id}"

  variables = {
    port = "${var.listener_port}"
  }

  depends_on = "${var.gateway_depends}"
}

resource "aws_security_group" "service_egress_security_group" {
  name        = "${var.namespace}-${var.environment}-service_egress_security_group"
  description = "Allow traffic from service"
  vpc_id      = "${var.vpc_id}"

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags {
    Name = "${var.namespace}-${var.environment}-egress"
  }
}
