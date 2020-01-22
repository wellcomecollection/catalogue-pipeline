resource "aws_security_group" "egress_security_group" {
  name        = "${var.namespace}_egress"
  description = "Allows all egress traffic from the group"
  vpc_id      = local.vpc_id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.namespace}"
  }
}

resource "aws_security_group" "interservice_security_group" {
  name        = "${var.namespace}_interservice_security_group"
  description = "Allow traffic between services"
  vpc_id      = "${local.vpc_id}"

  ingress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"
    self      = true
  }

  tags = {
    Name = "${var.namespace}-interservice"
  }
}

module "vpc_endpoints" {
  source = "github.com/wellcometrust/terraform//network/prebuilt/vpc/interface_endpoints?ref=bc8f95f"

  vpc_id            = local.vpc_id
  security_group_id = aws_security_group.egress_security_group.id
  subnet_ids        = local.private_subnets
}
