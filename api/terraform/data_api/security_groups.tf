resource "aws_security_group" "egress_security_group" {
  name        = "${var.namespace}-egress_security_group"
  description = "Allow outbound traffic to the Internet"
  vpc_id      = local.vpc_id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.namespace}-egress"
  }
}
