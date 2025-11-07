module "pipeline_step" {
  source = "github.com/wellcomecollection/terraform-aws-lambda.git?ref=v1.2.0"

  name         = local.name
  package_type = "Image"
  image_uri    = "${data.aws_ecr_repository.repository.repository_url}:${local.image_tag}"
  timeout      = var.timeout
  memory_size  = var.memory_size
  description  = var.description
  publish      = true

  vpc_config = var.vpc_config

  environment = {
    variables = local.environment_variables_with_secrets
  }

  image_config = var.image_config
}
