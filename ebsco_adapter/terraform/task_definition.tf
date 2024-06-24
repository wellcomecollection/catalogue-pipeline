module "app_container_definition" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/container_definition?ref=v3.13.1"
  name   = "ebsco-adapter-ftp"

  image = "${aws_ecr_repository.ebsco_adapter.repository_url}:latest"

  environment = {
    FTP_SERVER     = aws_ssm_parameter.ebsco_adapter_ftp_server.value
    FTP_USERNAME   = aws_ssm_parameter.ebsco_adapter_ftp_username.value
    FTP_REMOTE_DIR = aws_ssm_parameter.ebsco_adapter_ftp_remote_dir.value
    CUSTOMER_ID    = aws_ssm_parameter.ebsco_adapter_customer_id.value
    FTP_PASSWORD   = aws_ssm_parameter.ebsco_adapter_ftp_password.value
    OUTPUT_TOPIC_ARN = module.ebsco_adapter_output_topic.arn
    S3_BUCKET      = aws_s3_bucket.ebsco_adapter.bucket
    S3_PREFIX      = "prod"
  }

  log_configuration = module.log_router_container.container_log_configuration
}

module "log_router_container" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/firelens"
  namespace = local.namespace

  use_privatelink_endpoint = true
}

module "log_router_container_secrets_permissions" {
  source    = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/secrets"
  secrets   = module.log_router_container.shared_secrets_logging
  role_name = module.ftp_task_definition.task_execution_role_name
}

module "ftp_task_definition" {
  source = "git::github.com/wellcomecollection/terraform-aws-ecs-service.git//modules/task_definition"

  cpu    = 2048
  memory = 4096

  container_definitions = [
    module.log_router_container.container_definition,
    module.app_container_definition.container_definition
  ]

  launch_types = ["FARGATE"]
  task_name    = local.namespace
}
