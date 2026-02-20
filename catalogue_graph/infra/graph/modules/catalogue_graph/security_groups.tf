resource "aws_security_group" "neptune_security_group" {
  name   = "${var.namespace}-neptune"
  vpc_id = data.aws_vpc.vpc.id
}

# Allow any ingress traffic so that we can reach the cluster via its public endpoint.
resource "aws_vpc_security_group_ingress_rule" "neptune_ingress" {
  security_group_id = aws_security_group.neptune_security_group.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# Allow any egress traffic. The Neptune cluster needs to be able to reach the bulk loader S3 bucket.
resource "aws_vpc_security_group_egress_rule" "neptune_egress" {
  security_group_id = aws_security_group.neptune_security_group.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}
