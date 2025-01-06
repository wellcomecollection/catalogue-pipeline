# A Network Load Balancer for accessing the Neptune cluster from outside of the VPC.
# See https://aws-samples.github.io/aws-dbs-refarch-graph/src/connecting-using-a-load-balancer/.
# TODO: This only exists for testing purposes and should be destroyed before we switch to production.

resource "aws_lb" "neptune_experimental_network_lb" {
  name               = "neptune-test"
  internal           = false
  load_balancer_type = "network"
  security_groups    = [aws_security_group.neptune_lb_security_group.id]
  subnets            = local.public_subnets
}

# Create a new target group and attach the IP of the Neptune cluster
resource "aws_lb_target_group" "neptune_instance" {
  name        = "neptune-test-cluster"
  port        = 8182
  protocol    = "TCP"
  vpc_id      = data.aws_vpc.vpc.id
  target_type = "ip"
}

resource "aws_lb_target_group_attachment" "neptune_instance_attachment" {
  target_group_arn = aws_lb_target_group.neptune_instance.arn
  # Hardcode the private IP of the Neptune cluster. AWS does not guarantee that the IP will stay static, so we might
  # have to manually change this from time to time. I think this is okay for an experimental database, and overall
  # this setup is still more convenient than only being able to connect from within the VPC.
  # If it starts bothering us, we can create a Lambda function for dynamically updating the target group IP, as outlined
  # here: https://aws-samples.github.io/aws-dbs-refarch-graph/src/connecting-using-a-load-balancer/
  target_id        = "172.42.174.101"
}


# Forward traffic to the Neptune target group
resource "aws_lb_listener" "listener" {
  load_balancer_arn = aws_lb.neptune_experimental_network_lb.arn
  port              = "8182"
  protocol          = "TCP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.neptune_instance.arn
  }
}

# Create a security group allowing all ingress traffic. Limit egress traffic to the developer VPC.
resource "aws_security_group" "neptune_lb_security_group" {
  name   = "neptune-load-balancer"
  vpc_id = data.aws_vpc.vpc.id
}

resource "aws_vpc_security_group_ingress_rule" "neptune_lb_ingress" {
  security_group_id = aws_security_group.neptune_lb_security_group.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_vpc_security_group_egress_rule" "neptune_lb_egress" {
  security_group_id = aws_security_group.neptune_lb_security_group.id
  cidr_ipv4         = data.aws_vpc.vpc.cidr_block
  ip_protocol       = "-1"
}

resource "aws_secretsmanager_secret" "neptune_nlb_url" {
  name = "NeptuneTest/LoadBalancerUrl"
}

resource "aws_secretsmanager_secret_version" "neptune_nlb_endpoint_url" {
  secret_id     = aws_secretsmanager_secret.neptune_nlb_url.id
  secret_string = "https://${aws_lb.neptune_experimental_network_lb.dns_name}:8182"
}

