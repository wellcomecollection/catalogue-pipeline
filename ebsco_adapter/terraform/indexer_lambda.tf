module "indexer_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "ebsco-adapter-indexer"
  description = "Indexes EBSCO fields into the reporting cluster."
  runtime     = "python3.10"

  filename    = data.archive_file.empty_zip.output_path
  handler     = "main.lambda_handler"
  memory_size = 512
  timeout     = 60 // 1 minute

#  error_alarm_topic_arn = data.terraform_remote_state.monitoring.outputs["platform_lambda_error_alerts_topic_arn"]

  environment = {
    variables = {
      ES_INDEX = "ebsco_fields"
    }
  }

  depends_on = [
    aws_s3_bucket.ebsco_adapter,
  ]
}

data "aws_iam_policy_document" "read_ebsco_adapter_bucket" {
  statement {
    actions = [
      "s3:GetObject",
    ]

    resources = [
      "${aws_s3_bucket.ebsco_adapter.arn}/*"
    ]
  }
}

data "aws_iam_policy_document" "allow_secret_read" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:reporting/es_host*",
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:reporting/ebsco_indexer*"
    ]
  }
}

data "aws_iam_policy_document" "allow_sqs_receive_message" {
  statement {
    actions = [
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl"
    ]
    resources = [
      aws_sqs_queue.indexer_message_queue.arn
    ]
  }
}

resource "aws_iam_role_policy" "read_secrets_policy" {
  role   = module.indexer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_secret_read.json
}

resource "aws_iam_role_policy" "indexer_lambda_policy" {
  role   = module.indexer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.read_ebsco_adapter_bucket.json
}

resource "aws_iam_role_policy" "indexer_lambda_sqs_policy" {
  role   = module.indexer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_sqs_receive_message.json
}

resource "aws_sns_topic_subscription" "indexer_sqs_subscription" {
  topic_arn = module.ebsco_adapter_output_topic.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.indexer_message_queue.arn
}

resource "aws_sqs_queue" "indexer_message_queue" {
  name = "ebsco-indexer-message-queue"
  visibility_timeout_seconds = 90
}

data "aws_iam_policy_document" "indexer_message_queue_policy_data" {
  statement {
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.indexer_message_queue.arn]
    condition {
      test     = "ArnEquals"
      values   = [module.ebsco_adapter_output_topic.arn]
      variable = "aws:SourceArn"
    }
    principals {
      identifiers = ["sns.amazonaws.com"]
      type        = "Service"
    }
  }
}

resource "aws_sqs_queue_policy" "indexer_message_queue_policy" {
  queue_url = aws_sqs_queue.indexer_message_queue.id
  policy    = data.aws_iam_policy_document.indexer_message_queue_policy_data.json
}

resource "aws_lambda_event_source_mapping" "sqs_to_indexer_lambda" {
  event_source_arn                   = aws_sqs_queue.indexer_message_queue.arn
  function_name                      = module.indexer_lambda.lambda.function_name
  batch_size                         = 10
  enabled                            = true
  maximum_batching_window_in_seconds = 60
  scaling_config {
    maximum_concurrency = 10
  }
}

resource "aws_lambda_permission" "allow_indexer_lambda_sqs_trigger" {
  action        = "lambda:InvokeFunction"
  function_name = module.indexer_lambda.lambda.function_name
  principal     = "sqs.amazonaws.com"
  source_arn    = aws_sqs_queue.indexer_message_queue.arn
}
