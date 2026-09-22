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
  schedule_enabled      = false # Paused for the OAI-PMH prod switch (platform#6717) until the store rebuild (platform#6541)
  repository_url        = data.aws_ecr_repository.unified_pipeline_lambda.repository_url
  event_bus_name        = aws_cloudwatch_event_bus.event_bus.name
  ecs_cluster_arn       = aws_ecs_cluster.adapters.arn
  subnets               = local.private_subnets
  security_group_ids    = [aws_security_group.adapter_egress.id]
  task_repository_url   = data.aws_ecr_repository.unified_pipeline_task.repository_url
  enable_reconciliation = true
}

module "folio" {
  source                 = "./modules/adapter"
  namespace              = "folio"
  steps_namespace        = "oai_pmh"
  s3_bucket_name         = "wellcomecollection-platform-folio-adapter"
  schedule_expression    = "rate(15 minutes)"
  schedule_enabled       = true
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

  # The FOLIO dev server shares the catalogue VPC, so the ENIs go in the same
  # private subnets as the adapter ECS tasks above. See folio_dev_sandbox.tf.
  folio_dev_target_enabled     = local.folio_dev_target_enabled
  folio_dev_subnets            = local.folio_dev_target_enabled ? local.private_subnets : []
  folio_dev_security_group_ids = [aws_security_group.folio_sync_dev.id]

  # Scheduled runs target the sandbox and write for real, so the whole pipeline
  # is exercised end to end before any of it writes to production. dry_run is
  # clamped in the module so this can only ever write while the target is dev.
  folio_default_target = "dev"
  dry_run_default      = false

  # The sandbox is stopped out of hours by folio-dev-server, and the Axiell
  # adapter publishes every 15 minutes, so the trigger is confined to the
  # working day. Starts an hour after the sandbox does, to let Kong come up.
  trigger_window = local.folio_dev_target_enabled ? {
    start_expression = "cron(0 9 ? * MON-FRI *)"
    stop_expression  = "cron(30 17 ? * MON-FRI *)"
    timezone         = "Europe/London"
  } : null
}
