resource "aws_lb" "catalogue_api" {
  name               = local.namespace_hyphen
  internal           = true
  load_balancer_type = "network"
  subnets            = local.private_subnets
}

data "aws_vpc" "vpc" {
  id = "${local.vpc_id}"
}

resource "aws_security_group" "service_lb_ingress_security_group" {
  name        = "${local.namespace}-service_lb_ingress_security_group"
  description = "Allow traffic between services and NLB"
  vpc_id      = "${local.vpc_id}"

  ingress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["${data.aws_vpc.vpc.cidr_block}"]
  }

  tags = {
    Name = "${local.namespace}-lb-ingress"
  }
}
