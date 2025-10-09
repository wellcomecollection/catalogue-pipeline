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

# Security group intended for the Neptune cluster
# Allows all traffic from services with the same security group
resource "aws_security_group" "neptune_service_security_group" {
  name   = "catalogue-graph-neptune-interservice"
  vpc_id = data.aws_vpc.vpc.id
}

# Self-referencing ingress rule security group for the Neptune cluster
resource "aws_vpc_security_group_ingress_rule" "neptune_service_ingress" {
  security_group_id            = aws_security_group.neptune_security_group.id
  referenced_security_group_id = aws_security_group.neptune_service_security_group.id
  ip_protocol                  = "-1"
}

# Self-referencing egress rule security group for the Neptune cluster
resource "aws_vpc_security_group_egress_rule" "neptune_service_egress" {
  security_group_id            = aws_security_group.neptune_security_group.id
  referenced_security_group_id = aws_security_group.neptune_service_security_group.id
  ip_protocol                  = "-1"
}
