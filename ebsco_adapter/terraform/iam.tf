resource "aws_iam_role_policy" "ftp_adapter_publish_to_topic" {
  policy = module.ebsco_adapter_output_topic.publish_policy
  role   = module.ftp_task.task_role_name
}

data "aws_iam_policy_document" "ebsco_s3_bucket_full_access" {
  statement {
    actions = [
      "s3:ListBucket",
    ]

    resources = [
      aws_s3_bucket.ebsco_adapter.arn
    ]
  }

  statement {
    actions = [
      "s3:*Object",
    ]

    resources = [
      "${aws_s3_bucket.ebsco_adapter.arn}/*"
    ]
  }
}

# a policy for publishing cloudwatch metrics

data "aws_iam_policy_document" "ebsco_adapter_publish_metrics" {
  statement {
    actions = [
      "cloudwatch:PutMetricData",
    ]

    resources = ["*"]
  }
}

resource "aws_iam_policy" "ebsco_adapter_publish_metrics" {
  name        = "ebsco_adapter_publish_metrics"
  description = "Allow the ebsco_adapter to publish metrics to CloudWatch"
  policy      = data.aws_iam_policy_document.ebsco_adapter_publish_metrics.json
}

resource "aws_iam_role_policy_attachment" "ebsco_adapter_publish_metrics" {
  policy_arn = aws_iam_policy.ebsco_adapter_publish_metrics.arn
  role       = module.ftp_task.task_role_name
}

resource "aws_iam_policy" "ebsco_s3_bucket_full_access" {
  name        = "ebsco_s3_bucket_full_access"
  description = "Allow full access to the ebsco_adapter S3 bucket"
  policy      = data.aws_iam_policy_document.ebsco_s3_bucket_full_access.json
}

resource "aws_iam_role_policy_attachment" "ftp_adapter_s3_bucket_full_access" {
  policy_arn = aws_iam_policy.ebsco_s3_bucket_full_access.arn
  role       = module.ftp_task.task_role_name
}

# EventBridge scheduler IAM resources

resource "aws_iam_role" "eventbridge_task_scheduler" {
  name = "eventbridge-task-scheduler-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = ["scheduler.amazonaws.com"]
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "eventbridge_task_scheduler" {
  policy_arn = aws_iam_policy.eventbridge_task_scheduler.arn
  role       = aws_iam_role.eventbridge_task_scheduler.name
}

resource "aws_iam_policy" "eventbridge_task_scheduler" {
  name = "eventbridge-task-scheduler-policy"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow",
        Action = [
          "ecs:RunTask"
        ]
        Resource = ["${local.task_definition_arn_latest}:*"]
      },
      {
        Effect = "Allow",
        Action = [
          "iam:PassRole"
        ]
        Resource = [module.ftp_task.task_role_arn, module.ftp_task.task_execution_role_arn]
      },
    ]
  })
}
