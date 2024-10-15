locals {
  namespace = "catalogue-${var.pipeline_date}"

  name      = "${local.namespace}-${var.service_name}"
  image_tag = var.tag_override != "" ? var.tag_override : "env.${var.pipeline_date}"

  queue_name = var.queue_config.name == null ? trim("${local.name}_lambda_input", "_") : var.queue_config.name
}

data "aws_ecr_repository" "repository" {
  name = var.ecr_repository_name
}

data "aws_ecr_image" "lambda_image" {
  repository_name = data.aws_ecr_repository.repository.name
  image_tag       = local.image_tag
}
