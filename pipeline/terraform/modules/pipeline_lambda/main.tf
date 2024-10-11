module "pipeline_step" {
  source = "github.com/wellcomecollection/terraform-aws-lambda.git?ref=v1.1.1"

  name         = local.name
  package_type = "Image"
  image_uri    = "${data.aws_ecr_repository.repository.repository_url}@${data.aws_ecr_image.lambda_image.id}"
  timeout      = var.timeout
  memory_size  = var.memory_size
  description  = var.description

  environment = {
    variables = var.environment_variables
  }
}
