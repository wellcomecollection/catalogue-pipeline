data "aws_ecr_repository" "unified_pipeline_lambda" {
  name = "uk.ac.wellcome/unified_pipeline_lambda"
}

module "ebsco" {
  source              = "./modules/adapter"
  namespace           = "ebsco"
  s3_bucket_name      = "wellcomecollection-platform-ebsco-adapter"
  schedule_expression = "cron(0 2 * * ? *)" # Daily at 2 AM UTC
  repository_url      = data.aws_ecr_repository.unified_pipeline_lambda.repository_url
  event_bus_name      = aws_cloudwatch_event_bus.event_bus.name
  ecs_cluster_arn     = aws_ecs_cluster.adapters.arn
  subnets             = local.private_subnets
  security_group_ids  = [aws_security_group.adapter_egress.id]
  task_repository_url = data.aws_ecr_repository.unified_pipeline_task.repository_url
}

module "axiell" {
  source                = "./modules/adapter"
  namespace             = "axiell"
  steps_namespace       = "oai_pmh"
  s3_bucket_name        = "wellcomecollection-platform-axiell-adapter"
  schedule_expression   = "rate(15 minutes)"
  repository_url        = data.aws_ecr_repository.unified_pipeline_lambda.repository_url
  event_bus_name        = aws_cloudwatch_event_bus.event_bus.name
  ecs_cluster_arn       = aws_ecs_cluster.adapters.arn
  subnets               = local.private_subnets
  security_group_ids    = [aws_security_group.adapter_egress.id]
  task_repository_url   = data.aws_ecr_repository.unified_pipeline_task.repository_url
  enable_reconciliation = true
}

module "folio" {
  source              = "./modules/adapter"
  namespace           = "folio"
  steps_namespace     = "oai_pmh"
  s3_bucket_name      = "wellcomecollection-platform-folio-adapter"
  schedule_expression = "rate(15 minutes)"
  # Paused: overlapping runs were colliding on Iceberg commits, causing a
  # self-sustaining pileup of failed executions. Re-enable once resolved.
  schedule_enabled       = false
  repository_url         = data.aws_ecr_repository.unified_pipeline_lambda.repository_url
  event_bus_name         = aws_cloudwatch_event_bus.event_bus.name
  ecs_cluster_arn        = aws_ecs_cluster.adapters.arn
  subnets                = local.private_subnets
  security_group_ids     = [aws_security_group.adapter_egress.id]
  task_repository_url    = data.aws_ecr_repository.unified_pipeline_task.repository_url
  enable_item_enrichment = true
}

# Event bus to enable communication with the current pipeline
# This is a shared bus intended to be used by all new adapters,
# but there's currently no other users.
resource "aws_cloudwatch_event_bus" "event_bus" {
  name = "catalogue-pipeline-adapter-event-bus"
}

# Axiell to Folio outbound sync. Deployed as part of this stack (shares
# terraform/adapters.tfstate). Listens for axiell.adapter.completed on the shared
# bus and upserts changed records into FOLIO Inventory. Runs the sync handler out
# of the shared unified_pipeline_lambda image; build/deploy locally with
# `scripts/deploy_lambda.sh axiell-folio-sync-adapter-lambda`. OKAPI credentials come from SSM
# at runtime (seeded as a placeholder).
module "axiell_folio_sync" {
  source               = "./modules/axiell_folio_sync"
  namespace            = "axiell-folio-sync"
  repository_url       = data.aws_ecr_repository.unified_pipeline_lambda.repository_url
  event_bus_name       = aws_cloudwatch_event_bus.event_bus.name
  s3_table_bucket_arn  = aws_s3tables_table_bucket.axiell_table_bucket.arn
  manifest_bucket_name = "wellcomecollection-axiell-folio-sync-manifests"

  # Off by default; see folio_dev_sandbox.tf for what enabling entails. The FOLIO
  # dev server is in the catalogue VPC, so the ENIs go in the same private
  # subnets as the adapter ECS tasks above.
  folio_dev_target_enabled     = local.folio_dev_target_enabled
  folio_dev_subnets            = local.folio_dev_target_enabled ? local.private_subnets : []
  folio_dev_security_group_ids = aws_security_group.folio_sync_dev[*].id

  # The scheduled pipeline stays on production. Reaching the sandbox is a
  # per-invocation opt-in: {"folio_target": "dev"} on the event. Setting this to
  # "dev" would redirect the every-15-minutes automated runs too, which is not
  # what we want while the sandbox is only up during working hours.
  folio_default_target = "prod"

  # The sandbox's OKAPI connection. The url tracks the instance's live private IP
  # (see folio_dev_sandbox.tf); the password is read from the Secrets Manager
  # entry the sandbox already publishes, so nothing here is filled in by hand.
  folio_dev_okapi = local.folio_dev_target_enabled ? {
    url                = local.folio_dev_okapi_url
    tenant             = "diku"
    username           = "diku_admin"
    password_secret_id = "folio-sandbox/diku-admin-password"
  } : null
}
