# A Network Load Balancer for accessing the Neptune cluster from outside of the VPC.
# See https://aws-samples.github.io/aws-dbs-refarch-graph/src/connecting-using-a-load-balancer/.

resource "aws_lb" "neptune_network_load_balancer" {
  name               = "catalogue-graph-neptune-nlb"
  internal           = false
  load_balancer_type = "network"
  security_groups    = [aws_security_group.neptune_lb_security_group.id]
  subnets            = var.public_subnets
}

# Create a new target group and attach the IP of the Neptune cluster
resource "aws_lb_target_group" "neptune_instance" {
  name        = "neptune-catalogue-graph-cluster"
  port        = 8182
  protocol    = "TLS"
  vpc_id      = data.aws_vpc.vpc.id
  target_type = "ip"
}

resource "aws_lb_target_group_attachment" "neptune_instance_attachment" {
  target_group_arn = aws_lb_target_group.neptune_instance.arn
  # Hardcode the private IP of the Neptune cluster. AWS does not guarantee that the IP will stay static.
  # If we start seeing frequent IP changes, we can create a Lambda function for dynamically updating the target group
  # IP, as outlined here: https://aws-samples.github.io/aws-dbs-refarch-graph/src/connecting-using-a-load-balancer/
  target_id = data.aws_secretsmanager_secret_version.neptune_cluster_private_ip.secret_string
}

resource "aws_secretsmanager_secret" "neptune_cluster_private_ip" {
  name = "${var.namespace}/neptune-nlb-private-ip"
}

data "aws_secretsmanager_secret_version" "neptune_cluster_private_ip" {
  secret_id = aws_secretsmanager_secret.neptune_cluster_private_ip.id
}

# A custom certificate which will be used for TLS termination
module "catalogue_graph_nlb_certificate" {
  source = "github.com/wellcomecollection/terraform-aws-acm-certificate?ref=v1.0.0"

  domain_name = var.public_url
  zone_id     = data.aws_route53_zone.weco_zone.id

  providers = {
    aws     = aws
    aws.dns = aws.dns
  }
}

# Terminate TLS using the custom certificate and forward traffic to the Neptune target group
resource "aws_lb_listener" "listener" {
  load_balancer_arn = aws_lb.neptune_network_load_balancer.arn
  port              = "443"
  protocol          = "TLS"

  certificate_arn = module.catalogue_graph_nlb_certificate.arn

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
  name = "${var.namespace}/neptune-nlb-url"
}

resource "aws_secretsmanager_secret_version" "neptune_nlb_endpoint_url" {
  secret_id     = aws_secretsmanager_secret.neptune_nlb_url.id
  secret_string = "https://${var.public_url}"
}

data "aws_route53_zone" "weco_zone" {
  provider = aws.dns
  name     = "wellcomecollection.org."
}

# Add an alias A record to the wellcomecollection.org hosted zone, which maps the catalogue graph domain name
# to the NLB
resource "aws_route53_record" "catalogue_graph_nlb_record" {
  provider = aws.dns
  zone_id  = data.aws_route53_zone.weco_zone.id
  name     = var.public_url
  type     = "A"

  alias {
    name                   = aws_lb.neptune_network_load_balancer.dns_name
    zone_id                = aws_lb.neptune_network_load_balancer.zone_id
    evaluate_target_health = false
  }
}
