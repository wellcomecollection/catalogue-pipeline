# A scheduled scan-and-fan-out state machine:
#   ConstructEvent (build the window from the schedule, or pass through replay input)
#     -> FindWork (Lambda: ids in scope, partitioned to S3)
#       -> ProcessPartitions (Map: one worker per partition ref, bounded by max_concurrency)
#
# The find-work Lambda only discovers and slices; the injected worker state
# does the long-running processing, one bounded partition at a time.

module "find_work_lambda" {
  source = "../pipeline_lambda"

  service_name = var.find_work_lambda.service_name
  description  = var.find_work_lambda.description

  pipeline_date       = var.pipeline_date
  ecr_repository_name = var.ecr_repository_name

  image_config = {
    command = var.find_work_lambda.command
  }

  memory_size = var.find_work_lambda.memory_size
  timeout     = var.find_work_lambda.timeout

  environment_variables = var.find_work_lambda.environment_variables

  vpc_config = var.vpc_config
}

# The Lambda reads ES credentials from Secrets Manager at runtime.
resource "aws_iam_role_policy" "find_work_secret_read" {
  role   = module.find_work_lambda.lambda_role_name
  policy = var.find_work_secret_read_policy_json
}

# find_work writes each partition's ids to S3 (pass-by-reference) so the state
# machine Map payload stays small; each worker reads its partition back.
data "aws_iam_policy_document" "find_work_s3_write" {
  statement {
    effect    = "Allow"
    actions   = ["s3:PutObject"]
    resources = var.partition_s3_arns
  }
}

resource "aws_iam_role_policy" "find_work_s3_write" {
  role   = module.find_work_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.find_work_s3_write.json
}
