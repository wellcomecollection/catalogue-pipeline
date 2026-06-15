# Work-discovery Lambda for the image-inferrer state machine: queries
# images-initial for images modified within the scheduled window and partitions
# their ids for the Map fan-out.
# (The unified_pipeline_lambda ECR data source is declared in locals.tf.)

module "inference_find_work_lambda" {
  source = "../pipeline_lambda"

  service_name = "image-inference-find-work"
  description  = "Finds images needing inference within a time window and partitions them for the image-inferrer state machine."

  pipeline_date       = var.pipeline_date
  ecr_repository_name = data.aws_ecr_repository.unified_pipeline_lambda.name

  image_config = {
    command = ["inferrer.steps.find_work.lambda_handler"]
  }

  memory_size = 1024
  timeout     = 300 # 5 minutes

  vpc_config = {
    subnet_ids = local.network_config.subnets
    security_group_ids = [
      local.network_config.ec_privatelink_security_group_id,
      aws_security_group.egress.id,
    ]
  }
}

# The Lambda reads ES credentials from Secrets Manager at runtime.
resource "aws_iam_role_policy" "inference_find_work_secret_read" {
  role   = module.inference_find_work_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.inference_manager_pipeline_storage_secret_read.json
}
