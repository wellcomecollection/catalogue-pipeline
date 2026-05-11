module "loader_ecs_task" {
  source = "../../../../../pipeline/terraform/modules/ecs_task"

  task_name = "${var.namespace}-adapter-loader"
  # TODO: Change to :prod before merging — currently :dev for testing
  image = "${var.task_repository_url}:prod"

  cpu    = 4096
  memory = 16384

  environment = {
    S3_BUCKET = data.aws_s3_bucket.adapter.id
    S3_PREFIX = "prod"
  }
}

# IAM policy for writing to Iceberg table
resource "aws_iam_role_policy" "loader_task_iceberg_write" {
  role   = module.loader_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.iceberg_write.json
}

# IAM policy for reading from adapter S3 bucket
resource "aws_iam_role_policy" "loader_task_s3_read" {
  role   = module.loader_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.s3_read.json
}

resource "aws_iam_role_policy" "loader_task_s3_write" {
  role   = module.loader_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.s3_write.json
}

resource "aws_iam_role_policy" "loader_task_ssm_read" {
  role   = module.loader_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.ssm_read.json
}

resource "aws_iam_role_policy" "loader_task_cloudwatch_put_metric" {
  role   = module.loader_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.cloudwatch_put_metric_data.json
}

# Allow the ECS task to report back to Step Functions
resource "aws_iam_role_policy" "loader_task_sfn_callback" {
  role = module.loader_ecs_task.task_role_name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "states:SendTaskSuccess",
          "states:SendTaskFailure",
        ]
        Resource = [
          aws_sfn_state_machine.state_machine.arn,
        ]
      }
    ]
  })
}
