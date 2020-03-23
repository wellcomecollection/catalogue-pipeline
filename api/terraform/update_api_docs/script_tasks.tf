data "aws_ssm_parameter" "update_api_docs_image" {
  provider = aws.platform

  name = "/catalogue_api/images/latest/update_api_docs"
}

module "task" {
  source = "github.com/wellcomecollection/terraform-aws-ecs-service.git//task_definition/single_container?ref=v1.5.2"

  task_name = "update_api_docs"

  container_image = data.aws_ssm_parameter.update_api_docs_image.value

  cpu    = 256
  memory = 512

  aws_region = var.aws_region
}
