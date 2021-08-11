# There are two modes of DynamoDB capacity/pricing:
#
#   - On-Demand = autoscales on demand, pay for the write units you use
#   - Provisioned = set a fixed capacity upfront, pay for the capacity
#     whether or not you use it
#
# Most of the time, the On-Demand pricing is a better fit for us:
# we have long periods with no traffic, then brief spikes when an update
# comes in.  But during a reindex we know we have lots of traffic, so
# it seems sensible to provision capacity instead.
#
# Note: you can only change capacity mode once every 24 hours, see
# https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/switching.capacitymode.html

locals {
  graph_table_billing_mode = var.is_reindexing ? "PROVISIONED" : "PAY_PER_REQUEST"
  lock_table_billing_mode  = var.is_reindexing ? "PROVISIONED" : "PAY_PER_REQUEST"
}

# These numbers were chosen by running a reindex and seeing when the
# matcher started throttling.
module "matcher_graph_table_autoscaling" {
  source = "../modules/dynamodb_autoscaling"

  count = var.is_reindexing ? 1 : 0

  table_name = aws_dynamodb_table.matcher_graph_table.name

  max_read_capacity  = 600
  max_write_capacity = 1000
}

# These numbers were chosen by running a reindex and seeing when the
# matcher started throttling.
module "lock_table_autoscaling" {
  source = "../modules/dynamodb_autoscaling"

  count = var.is_reindexing ? 1 : 0

  table_name = aws_dynamodb_table.matcher_lock_table.name

  max_read_capacity  = 1500
  max_write_capacity = 2000
}

# Graph table
locals {
  graph_table_name = "${local.namespace_hyphen}_works-graph"
}

resource "aws_dynamodb_table" "matcher_graph_table" {
  name     = local.graph_table_name
  hash_key = "id"

  attribute {
    name = "id"
    type = "S"
  }

  attribute {
    name = "componentId"
    type = "S"
  }

  billing_mode   = local.graph_table_billing_mode

  global_secondary_index {
    name            = "work-sets-index"
    hash_key        = "componentId"
    projection_type = "ALL"
  }

  tags = {
    Name = local.graph_table_name
  }

  lifecycle {
    ignore_changes = [
      global_secondary_index,
      read_capacity,
      write_capacity
    ]
  }
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
      aws_dynamodb_table.matcher_graph_table.arn,
    ]
  }

  statement {
    actions = [
      "dynamodb:Query",
    ]

    resources = [
      "${aws_dynamodb_table.matcher_graph_table.arn}/index/*",
    ]
  }
}

# Lock table

locals {
  lock_table_name = "${local.namespace_hyphen}_matcher-lock-table"
}

resource "aws_dynamodb_table" "matcher_lock_table" {
  name     = local.lock_table_name
  hash_key = "id"

  billing_mode   = local.lock_table_billing_mode

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
  }

  ttl {
    attribute_name = "expires"
    enabled        = true
  }

  tags = {
    Name = local.lock_table_name
  }

  lifecycle {
    ignore_changes = [
      global_secondary_index,
      read_capacity,
      write_capacity
    ]
  }
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
      aws_dynamodb_table.matcher_lock_table.arn,
    ]
  }

  statement {
    actions = [
      "dynamodb:Query",
    ]

    resources = [
      "${aws_dynamodb_table.matcher_lock_table.arn}/index/*",
    ]
  }
}
