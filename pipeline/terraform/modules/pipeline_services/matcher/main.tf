# ──────────────────────────────────────────────
# Output topic
# ──────────────────────────────────────────────

module "matcher_output_topic" {
  source = "../../topic"

  name       = "${local.namespace}_output"
  role_names = [module.matcher_lambda.lambda_role_name]
}

# ──────────────────────────────────────────────
# Lambda
# ──────────────────────────────────────────────

module "matcher_lambda" {
  source = "../../pipeline_lambda"

  pipeline_date = var.pipeline_date
  service_name  = var.service_name
  vpc_config    = var.vpc_config

  environment_variables = {
    topic_arn = module.matcher_output_topic.arn

    dynamo_table            = aws_dynamodb_table.graph_table.id
    dynamo_index            = "work-sets-index"
    dynamo_lock_table       = aws_dynamodb_table.lock_table.id
    dynamo_lock_table_index = "context-ids-index"

    dynamo_lock_timeout = local.lock_timeout

    es_index = var.es_works_identified_index
  }

  secret_env_vars = var.secret_env_vars

  ecr_repository_name = var.ecr_repository_name
  queue_config        = var.queue_config
  timeout             = var.timeout
  memory_size         = var.memory_size
}

# ──────────────────────────────────────────────
# DynamoDB: Graph table
# ──────────────────────────────────────────────

resource "aws_dynamodb_table" "graph_table" {
  name     = "${local.base_namespace}_works-graph"
  hash_key = "id"

  attribute {
    name = "id"
    type = "S"
  }

  attribute {
    name = "subgraphId"
    type = "S"
  }

  billing_mode = local.graph_table_billing_mode

  point_in_time_recovery {
    enabled = true
  }

  read_capacity  = local.graph_table_billing_mode == "PROVISIONED" ? 600 : 0
  write_capacity = local.graph_table_billing_mode == "PROVISIONED" ? 1000 : 0

  global_secondary_index {
    name            = "work-sets-index"
    hash_key        = "subgraphId"
    projection_type = "ALL"

    read_capacity  = local.graph_table_billing_mode == "PROVISIONED" ? 600 : 0
    write_capacity = local.graph_table_billing_mode == "PROVISIONED" ? 1000 : 0
  }

  tags = {
    Name = "${local.base_namespace}_works-graph"
  }
}

resource "aws_iam_role_policy" "matcher_graph_readwrite" {
  role   = module.matcher_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.graph_table_readwrite.json
}

data "aws_iam_policy_document" "graph_table_readwrite" {
  statement {
    actions = [
      "dynamodb:BatchGetItem",
      "dynamodb:BatchWriteItem",
      "dynamodb:GetItem",
      "dynamodb:UpdateItem",
      "dynamodb:PutItem",
    ]

    resources = [
      aws_dynamodb_table.graph_table.arn,
    ]
  }

  statement {
    actions = [
      "dynamodb:Query",
    ]

    resources = [
      "${aws_dynamodb_table.graph_table.arn}/index/*",
    ]
  }
}

# ──────────────────────────────────────────────
# DynamoDB: Lock table
# ──────────────────────────────────────────────

resource "aws_dynamodb_table" "lock_table" {
  name     = "${local.namespace}-lock-table"
  hash_key = "id"

  billing_mode = local.lock_table_billing_mode

  read_capacity  = local.lock_table_billing_mode == "PROVISIONED" ? 1000 : 0
  write_capacity = local.lock_table_billing_mode == "PROVISIONED" ? 2500 : 0

  attribute {
    name = "id"
    type = "S"
  }

  attribute {
    name = "contextId"
    type = "S"
  }

  global_secondary_index {
    name            = "context-ids-index"
    hash_key        = "contextId"
    projection_type = "ALL"

    read_capacity  = local.lock_table_billing_mode == "PROVISIONED" ? 1000 : 0
    write_capacity = local.lock_table_billing_mode == "PROVISIONED" ? 2500 : 0

    warm_throughput {
      read_units_per_second  = 12000
      write_units_per_second = 6216
    }
  }

  ttl {
    attribute_name = "expires"
    enabled        = true
  }

  tags = {
    Name = "${local.namespace}-lock-table"
  }
}

resource "aws_iam_role_policy" "matcher_lock_readwrite" {
  role   = module.matcher_lambda.lambda_role_name
  policy = data.aws_iam_policy_document.lock_table_readwrite.json
}

data "aws_iam_policy_document" "lock_table_readwrite" {
  statement {
    actions = [
      "dynamodb:UpdateItem",
      "dynamodb:PutItem",
      "dynamodb:GetItem",
      "dynamodb:DeleteItem",
      "dynamodb:BatchWriteItem",
    ]

    resources = [
      aws_dynamodb_table.lock_table.arn,
    ]
  }

  statement {
    actions = [
      "dynamodb:Query",
    ]

    resources = [
      "${aws_dynamodb_table.lock_table.arn}/index/*",
    ]
  }
}
