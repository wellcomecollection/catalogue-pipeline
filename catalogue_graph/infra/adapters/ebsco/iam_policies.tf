# Write policy (create/update table + read/write data & metadata)
data "aws_iam_policy_document" "iceberg_write" {
  # Bucket-level operations needed for managing (creating/listing) namespaces & tables
  statement {
    actions = [
      "s3tables:CreateNamespace",
      "s3tables:GetNamespace",
      "s3tables:ListNamespaces",
      "s3tables:CreateTable",
      "s3tables:ListTables",
      "s3tables:GetTableBucket",
      "s3tables:GetTableMetadataLocation",
    ]
    resources = [
      "arn:aws:s3tables:eu-west-1:760097843905:bucket/wellcomecollection-platform-ebsco-adapter",
      "arn:aws:s3tables:eu-west-1:760097843905:bucket/wellcomecollection-platform-ebsco-adapter/*"
    ]
  }

  # Table-level operations for reading & writing Iceberg (metadata + data files commits)
  statement {
    actions = [
      "s3tables:GetTableMetadataLocation",
      "s3tables:ListTables",
      "s3tables:GetTable",
      "s3tables:GetTableData",
      "s3tables:PutTableData",
      "s3tables:UpdateTableMetadataLocation"
    ]

    resources = [
      "arn:aws:s3tables:eu-west-1:760097843905:bucket/wellcomecollection-platform-ebsco-adapter/table/*"
    ]
  }
}

# Policy for reading from the EBSCO adapter S3 bucket
data "aws_iam_policy_document" "s3_read" {
  statement {
    actions = [
      "s3:GetObject",
      "s3:ListBucket"
    ]

    resources = [
      "arn:aws:s3:::wellcomecollection-platform-ebsco-adapter",
      "arn:aws:s3:::wellcomecollection-platform-ebsco-adapter/*"
    ]
  }
}

# Policy for writing to the EBSCO adapter S3 bucket
data "aws_iam_policy_document" "s3_write" {
  statement {
    actions = [
      "s3:PutObject",
    ]

    resources = [
      "arn:aws:s3:::wellcomecollection-platform-ebsco-adapter/prod/ftp_v2/*",
    ]
  }
}

# Allow read ssm parameters
data "aws_iam_policy_document" "ssm_read" {
  statement {
    actions = [
      "ssm:GetParameter",
      "ssm:GetParameters",
      "ssm:GetParametersByPath"
    ]

    resources = [
      "arn:aws:ssm:eu-west-1:760097843905:parameter/catalogue_pipeline/ebsco_adapter/*"
    ]
  }

  # KMS permissions needed for WithDecryption=True on SecureString parameters
  statement {
    actions = [
      "kms:Decrypt"
    ]

    resources = [
      "arn:aws:kms:eu-west-1:760097843905:key/*"
    ]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.eu-west-1.amazonaws.com"]
    }
  }
}

# IAM Policy for State Machine to invoke Lambda functions
resource "aws_iam_policy" "state_machine_lambda_policy" {
  name        = "ebsco-adapter-state-machine-lambda-policy"
  description = "Allow state machine to invoke EBSCO adapter Lambda functions"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "lambda:InvokeFunction"
        ]
        Resource = [
          module.trigger_lambda.lambda.arn,
          module.loader_lambda.lambda.arn,
        ]
      }
    ]
  })
}

# IAM Policy allowing the state machine to put events onto the shared adapter event bus
resource "aws_iam_policy" "state_machine_eventbridge_put_policy" {
  name        = "ebsco-adapter-state-machine-eventbridge-put-policy"
  description = "Allow state machine to put events on the adapter event bus"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "events:PutEvents"
        ]
        Resource = [
          aws_cloudwatch_event_bus.event_bus.arn
        ]
      }
    ]
  })
}

# IAM Policy for State Machine CloudWatch Logging
resource "aws_iam_policy" "state_machine_logging_policy" {
  name        = "ebsco-adapter-state-machine-logging-policy"
  description = "Allow state machine to write logs to CloudWatch"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "logs:CreateLogDelivery",
          "logs:GetLogDelivery",
          "logs:UpdateLogDelivery",
          "logs:DeleteLogDelivery",
          "logs:ListLogDeliveries",
          "logs:PutResourcePolicy",
          "logs:DescribeResourcePolicies",
          "logs:DescribeLogGroups"
        ]
        Resource = "*"
      }
    ]
  })
}

# IAM Policy for EventBridge to start State Machine executions
resource "aws_iam_policy" "eventbridge_state_machine_policy" {
  name        = "ebsco-adapter-eventbridge-state-machine-policy"
  description = "Allow EventBridge to start EBSCO adapter state machine executions"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "states:StartExecution"
        ]
        Resource = [
          aws_sfn_state_machine.state_machine.arn
        ]
      }
    ]
  })
}