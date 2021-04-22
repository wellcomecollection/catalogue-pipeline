resource "aws_ecs_cluster" "catalogue_api" {
  name = "catalogue-api"
}

resource "aws_ecs_cluster" "stacks_api" {
  name = "stacks-api"
}
