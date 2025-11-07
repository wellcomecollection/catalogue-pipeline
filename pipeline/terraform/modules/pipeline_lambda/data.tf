locals {
  namespace = "catalogue-${var.pipeline_date}"

  name      = "${local.namespace}-${var.service_name}"
  image_tag = var.tag_override != "" ? "latest" : "env.${var.pipeline_date}"

  # Only set a queue name if a queue_config object has been supplied.
  # When queue_config is null, downstream queue resources are not created.
  queue_name = var.queue_config == null ? null : (var.queue_config.name == null ? trim("${local.name}_lambda_input", "_") : var.queue_config.name)
}

data "aws_ecr_repository" "repository" {
  name = var.ecr_repository_name
}
