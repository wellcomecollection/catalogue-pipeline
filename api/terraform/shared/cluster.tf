resource "aws_ecs_cluster" "catalogue_api" {
  name = replace(local.namespace, "_", "-")
}
