resource "aws_lb_target_group" "tcp" {

  # Must only contain alphanumerics and hyphens.
  name = replace(var.service_name, "_", "-")

  target_type = "ip"

  protocol = "TCP"
  port     = module.nginx_container.container_port
  vpc_id   = var.vpc_id

  # This is the amount of time that ECS will wait before killing a
  # deregistered target and so should be longer than the longest
  # expected request but not much more than that.
  deregistration_delay = 10

  health_check {
    protocol = "TCP"
    path = var.healthcheck_path
    interval = 10
    healthy_threshold = 3
    unhealthy_threshold = 3
  }
}

resource "aws_lb_listener" "tcp" {
  load_balancer_arn = var.load_balancer_arn
  port              = var.load_balancer_listener_port
  protocol          = "TCP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.tcp.arn
  }
}
