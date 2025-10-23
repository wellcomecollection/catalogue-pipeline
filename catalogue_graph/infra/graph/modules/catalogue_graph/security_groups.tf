resource "aws_security_group" "neptune_security_group" {
  name   = "${var.namespace}-neptune"
  vpc_id = data.aws_vpc.vpc.id
}

# Only allow ingress traffic from the VPC containing the cluster
resource "aws_vpc_security_group_ingress_rule" "neptune_ingress" {
  security_group_id = aws_security_group.neptune_security_group.id
  cidr_ipv4         = data.aws_vpc.vpc.cidr_block
  ip_protocol       = "-1"
}

# Allow any egress traffic. The Neptune cluster needs to be able to reach the bulk loader S3 bucket.
resource "aws_vpc_security_group_egress_rule" "neptune_egress" {
  security_group_id = aws_security_group.neptune_security_group.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}
