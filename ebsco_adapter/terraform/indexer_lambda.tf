module "indexer_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "ebsco-adapter-indexer"
  description = "Indexes EBSCO fields into the reporting cluster."
  runtime     = "python3.10"

  filename    = data.archive_file.empty_zip.output_path
  handler     = "main.lambda_handler"
  memory_size = 512
  timeout     = 60 // 1 minute

  error_alarm_topic_arn = data.terraform_remote_state.monitoring.outputs["platform_lambda_error_alerts_topic_arn"]

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
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:reporting/es_host*",
      "arn:aws:secretsmanager:eu-west-1:760097843905:secret:reporting/ebsco_indexer*"
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

# Add an SQS queue which will collect messages from SNS
module "indexer_message_queue" {
  source = "github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"

  queue_name = "ebsco-indexer-message-queue"

  topic_arns                 = [module.ebsco_adapter_output_topic.arn]
  visibility_timeout_seconds = 90
  max_receive_count          = 3
  alarm_topic_arn            = "arn:aws:sns:eu-west-1:760097843905:platform_dlq_non_empty_alarm"
}

# Allow the Lambda function to read from the queue
resource "aws_iam_role_policy" "indexer_lambda_sqs_policy" {
  role   = module.indexer_lambda.lambda_role.name
  policy = module.indexer_message_queue.read_policy
}

# This configures an EventSourceMapping which automatically polls the SQS queue for new messages and triggers
# the indexer Lambda function. All messages received in a 60 second window (defined by `maximum_batching_window_in_seconds`)
# are collected and sent to the Lambda for processing in batches of at most 10 messages (defined by `batch_size`).
# Additionally, the `maximum_concurrency` parameter ensures that there are at most 10 active indexer Lambda functions
# running at a time to make sure we don't overwhelm the Elasticsearch cluster.
resource "aws_lambda_event_source_mapping" "sqs_to_indexer_lambda" {
  event_source_arn                   = module.indexer_message_queue.arn
  function_name                      = module.indexer_lambda.lambda.function_name
  batch_size                         = 10
  enabled                            = true
  maximum_batching_window_in_seconds = 60
  scaling_config {
    maximum_concurrency = 10
  }
}

# Give the SQS queue permission to invoke the indexer lambda
resource "aws_lambda_permission" "allow_indexer_lambda_sqs_trigger" {
  action        = "lambda:InvokeFunction"
  function_name = module.indexer_lambda.lambda.function_name
  principal     = "sqs.amazonaws.com"
  source_arn    = module.indexer_message_queue.arn
}
