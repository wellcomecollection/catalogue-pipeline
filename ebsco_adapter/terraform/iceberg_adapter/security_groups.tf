resource "aws_security_group" "egress" {
  name        = "${local.namespace}_egress"
  description = "Allow egress traffic from the services"
  vpc_id      = local.network_config.vpc_id

  egress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"

    cidr_blocks = [
      "0.0.0.0/0",
    ]
  }

  tags = {
    Name = "${local.namespace}_egress"
  }
}
