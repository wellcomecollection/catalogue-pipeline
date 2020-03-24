resource "aws_security_group" "egress" {
  name        = "${local.namespace}_catalogue_api_services_egress"
  description = "Allows all egress traffic from the group"
  vpc_id      = local.vpc_id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${local.namespace}_catalogue_api_services"
  }
}

resource "aws_security_group" "interservice" {
  name        = "${local.namespace_hyphen}_interservice"
  description = "Allow traffic between services"
  vpc_id      = local.vpc_id

  ingress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"
    self      = true
  }

  tags = {
    Name = "${local.namespace_hyphen}-interservice"
  }
}
