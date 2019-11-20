module "egress_security_group" {
  source = "github.com/wellcometrust/terraform//network/prebuilt/vpc/egress_security_group?ref=v19.5.0"

  name = "${local.namespace_hyphen}_pipeline_services"

  vpc_id     = "${var.vpc_id}"
  subnet_ids = "${var.subnets}"
}

resource "aws_security_group" "service_egress" {
  name        = "${local.namespace_hyphen}_service_egress"
  description = "Allow traffic between services"
  vpc_id      = "${var.vpc_id}"

  egress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"

    cidr_blocks = [
      "0.0.0.0/0",
    ]
  }

  tags {
    Name = "${local.namespace_hyphen}-egress"
  }
}

resource "aws_security_group" "interservice" {
  name        = "${local.namespace_hyphen}_interservice"
  description = "Allow traffic between services"
  vpc_id      = "${var.vpc_id}"

  ingress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"
    self      = true
  }

  tags {
    Name = "${local.namespace_hyphen}-interservice"
  }
}
