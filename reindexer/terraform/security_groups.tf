resource "aws_security_group" "service_egress_security_group" {
  name   = "reindexer_service_egress_security_group"
  vpc_id = local.vpc_id

  # This description is a copy/paste error; this security group allows
  # outbound traffic, not interservice traffic.
  #
  # You can't change the description of a security group after creation,
  # but this conditional will stop it being propagated to any new instances.
  # If the VPC changes at some point and the correct description comes into use,
  # you can remove this conditional.
  description = local.vpc_id == "vpc-056bb88db6eb1b387" ? "Allow traffic between services" : "Allow outbound traffic"

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "reindexer-egress"
  }
}
