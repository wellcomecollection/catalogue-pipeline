resource "aws_security_group" "egress" {
  name        = "${local.namespace}_egress"
  description = "Allow egress traffic from the services"
  vpc_id      = data.aws_vpc.vpc.id

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

resource "aws_security_group" "neptune_service_security_group" {
  name   = "catalogue-graph-neptune-interservice"
  vpc_id = data.aws_vpc.vpc.id
}
