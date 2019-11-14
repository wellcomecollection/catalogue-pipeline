resource "aws_ecs_cluster" "cluster" {
  name = "${replace(local.namespace_underscores, "_", "-")}"
}
