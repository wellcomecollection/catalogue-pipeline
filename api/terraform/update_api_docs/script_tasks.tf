data "aws_ssm_parameter" "update_api_docs_image" {
  provider = aws.platform

  name = "/catalogue_api/images/latest/update_api_docs"
}

locals {
  update_api_docs_image = data.aws_ssm_parameter.update_api_docs_image.value
}

module "update_api_docs" {
  source        = "../modules/ecs_script_task"
  task_name     = "update_api_docs"
  app_uri       = local.update_api_docs_image
  task_role_arn = "${module.ecs_update_api_docs_iam.task_role_arn}"

  cpu    = 256
  memory = 256
}
