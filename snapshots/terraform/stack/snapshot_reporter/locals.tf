data "aws_secretsmanager_secret_version" "elastic_secret_id" {
  secret_id = local.elastic_secret_id
}

data "aws_secretsmanager_secret_version" "slack_secret_id" {
  secret_id = local.slack_secret_id
}

locals {
  elastic_secret_id = "catalogue/snapshots/read_user"
  slack_secret_id   = "snapshot_reporter/slack_webhook"
}
