module "pipeline_step" {
  source = "github.com/wellcomecollection/terraform-aws-lambda.git?ref=v1.2.0"

  name         = local.name
  package_type = "Image"
  image_uri    = "${data.aws_ecr_repository.repository.repository_url}:${local.image_tag}"
  timeout      = var.timeout
  memory_size  = var.memory_size
  description  = var.description
  # Versions are not consumed anywhere (state machines invoke the unqualified
  # ARN), and CI deploys via `update-function-code --publish` out of band, so
  # letting Terraform also publish only produced a perpetual version diff.
  publish = false

  vpc_config = var.vpc_config

  environment = {
    variables = local.environment_variables_with_secrets
  }

  image_config = var.image_config
}
