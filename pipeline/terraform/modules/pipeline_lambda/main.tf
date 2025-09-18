module "pipeline_step" {
  source = "github.com/wellcomecollection/terraform-aws-lambda.git?ref=v1.1.1"

  name         = local.name
  package_type = "Image"
  image_uri    = "${data.aws_ecr_repository.repository.repository_url}:latest"
  timeout      = var.timeout
  memory_size  = var.memory_size
  description  = var.description

  vpc_config = var.vpc_config

  environment = {
    variables = local.environment_variables_with_secrets
  }

  image_config = var.image_config
}
