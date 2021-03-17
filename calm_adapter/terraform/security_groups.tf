resource "aws_security_group" "egress" {
  name        = "${local.namespace}_egress"
  description = "Allows all egress traffic from the group"
  vpc_id      = local.vpc_id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}
