resource "aws_ecs_cluster" "data_api" {
  name = replace(var.namespace, "_", "-")
}
