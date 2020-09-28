resource "aws_security_group" "service_egress" {
  name        = "snapshot_generator_service_egress"
  description = "Allow egress traffic to service"
  vpc_id      = var.vpc_id

  egress {
    from_port = 0
    to_port   = 0
    protocol  = "-1"

    cidr_blocks = [
      "0.0.0.0/0",
    ]
  }
}
