resource "aws_security_group" "graph_pipeline_security_group" {
  name   = "${local.namespace}-security-group-${var.pipeline_date}"
  vpc_id = data.aws_vpc.vpc.id
}

#resource "aws_vpc_security_group_ingress_rule" "neptune_lambda_ingress" {
#  security_group_id = aws_security_group.graph_pipeline_security_group.id
#  cidr_ipv4         = "0.0.0.0/0"
#  ip_protocol       = "-1"
#}

resource "aws_vpc_security_group_egress_rule" "ingress" {
  security_group_id = aws_security_group.graph_pipeline_security_group.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}
