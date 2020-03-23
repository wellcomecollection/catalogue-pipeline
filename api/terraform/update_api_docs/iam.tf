module "ecs_update_api_docs_iam" {
  source    = "github.com/wellcomecollection/terraform-aws-ecs-service.git//task_definition/modules/iam_role?ref=v1.5.0"
  task_name = "update_api_docs"
}
