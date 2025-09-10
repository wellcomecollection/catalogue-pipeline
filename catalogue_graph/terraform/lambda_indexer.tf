module "indexer_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "catalogue-graph-indexer"
  description  = "Indexes nodes and edges into the Neptune catalogue graph cluster."
  package_type = "Image"
  image_uri    = "${aws_ecr_repository.unified_pipeline_lambda.repository_url}:prod"
  publish      = true

  // New versions are automatically deployed through a GitHub action.
  // To deploy manually, see `scripts/deploy_lambda_zip.sh`

  image_config = {
    command = ["indexer.lambda_handler"]
  }

  memory_size = 128
  timeout     = 60 // 1 minute

  vpc_config = {
    subnet_ids         = local.private_subnets
    security_group_ids = [aws_security_group.graph_indexer_lambda_security_group.id]
  }

  #  error_alarm_topic_arn = data.terraform_remote_state.monitoring.outputs["platform_lambda_error_alerts_topic_arn"]
}

resource "aws_iam_role_policy" "indexer_lambda_read_secrets_policy" {
  role   = module.indexer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_secret_read.json
}

# Create a security group allowing all ingress traffic. Limit egress traffic to the developer VPC.
resource "aws_security_group" "graph_indexer_lambda_security_group" {
  name   = "graph-indexer-lambda"
  vpc_id = data.aws_vpc.vpc.id
}

resource "aws_vpc_security_group_ingress_rule" "neptune_lambda_ingress" {
  security_group_id = aws_security_group.graph_indexer_lambda_security_group.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_vpc_security_group_egress_rule" "neptune_lambda_egress" {
  security_group_id = aws_security_group.graph_indexer_lambda_security_group.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# Add an SQS queue which will collect messages from SNS
module "indexer_message_queue" {
  source = "github.com/wellcomecollection/terraform-aws-sqs//queue?ref=v1.2.1"

  queue_name = "catalogue-graph-indexer-message-queue"

  topic_arns                 = [module.catalogue_graph_queries_topic.arn]
  visibility_timeout_seconds = 90
  max_receive_count          = 3
  alarm_topic_arn            = "arn:aws:sns:eu-west-1:760097843905:platform_dlq_non_empty_alarm"
}

data "aws_iam_policy_document" "neptune_write" {
  statement {
    actions = [
      "neptune-db:*"
    ]

    resources = [
      "*"
    ]
  }
}

data "aws_iam_policy_document" "allow_sqs_receive_message" {
  statement {
    actions = [
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl",
      "sqs:ChangeMessageVisibilityBatch",
      "sqs:ChangeMessageVisibility"
    ]
    resources = [
      module.indexer_message_queue.arn
    ]
  }
}

# Allow the Lambda function to read from the queue
resource "aws_iam_role_policy" "indexer_lambda_sqs_policy" {
  role   = module.indexer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_sqs_receive_message.json
}

resource "aws_iam_role_policy" "indexer_lambda_neptune_policy" {
  role   = module.indexer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.neptune_write.json
}

# This configures an EventSourceMapping which automatically polls the SQS queue for new messages and triggers
# the indexer Lambda function.
resource "aws_lambda_event_source_mapping" "sqs_to_indexer_lambda" {
  event_source_arn                   = module.indexer_message_queue.arn
  function_name                      = module.indexer_lambda.lambda.function_name
  batch_size                         = 5 # Maximum number of messages processed in a single Lambda run.
  enabled                            = true
  maximum_batching_window_in_seconds = 60
  scaling_config {
    maximum_concurrency = 20 # Maximum number of active indexer Lambda functions running at a time
  }
}

# Give the SQS queue permission to invoke the indexer lambda
resource "aws_lambda_permission" "allow_indexer_lambda_sqs_trigger" {
  action        = "lambda:InvokeFunction"
  function_name = module.indexer_lambda.lambda.function_name
  principal     = "sqs.amazonaws.com"
  source_arn    = module.indexer_message_queue.arn
}
