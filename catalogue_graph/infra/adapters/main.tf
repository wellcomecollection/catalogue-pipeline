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
  schedule_enabled      = true # Resumed after the switchover store rebuild (platform#6541 phase 4); the trigger emits one catch-up window from the cursor
  repository_url        = data.aws_ecr_repository.unified_pipeline_lambda.repository_url
  event_bus_name        = aws_cloudwatch_event_bus.event_bus.name
  ecs_cluster_arn       = aws_ecs_cluster.adapters.arn
  subnets               = local.private_subnets
  security_group_ids    = [aws_security_group.adapter_egress.id]
  task_repository_url   = data.aws_ecr_repository.unified_pipeline_task.repository_url
  enable_reconciliation = true
}

# The Axiell UAT adapter is added to connect UAT data to the FOLIO test
# instance without touching live data (wellcomecollection/platform#6920). The schedule stays off until the
# UAT OAI-PMH endpoint and token are in SSM (ssm_parameters.tf). Reconciliation
# is on because the FOLIO sync reads its deletion facts and reconciler tables on
# every incremental run, and fails if they are missing.
module "axiell_uat" {
  source                = "./modules/adapter"
  namespace             = "axiell_uat"
  steps_namespace       = "oai_pmh"
  s3_bucket_name        = "wellcomecollection-platform-axiell-uat-adapter"
  schedule_expression   = "rate(15 minutes)"
  schedule_enabled      = false
  repository_url        = data.aws_ecr_repository.unified_pipeline_lambda.repository_url
  event_bus_name        = aws_cloudwatch_event_bus.event_bus.name
  ecs_cluster_arn       = aws_ecs_cluster.adapters.arn
  subnets               = local.private_subnets
  security_group_ids    = [aws_security_group.adapter_egress.id]
  task_repository_url   = data.aws_ecr_repository.unified_pipeline_task.repository_url
  enable_reconciliation = true

  # The module reads its S3 bucket through a data source, which would fail at
  # plan time before the bucket exists.
  depends_on = [
    aws_s3_bucket.axiell_uat_adapter,
    aws_s3tables_table_bucket.axiell_uat_table_bucket,
  ]
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
# terraform/adapters.tfstate). Listens for axiell.adapter.completed and
# axiell_uat.adapter.completed on the shared bus and upserts changed records into
# FOLIO Inventory. Runs the sync handler out of the shared unified_pipeline_lambda
# image, which CI builds from main and the deploy workflow rolls out. OKAPI
# credentials come from SSM at runtime (seeded as a placeholder).
module "axiell_folio_sync" {
  source         = "./modules/axiell_folio_sync"
  namespace      = "axiell-folio-sync"
  repository_url = data.aws_ecr_repository.unified_pipeline_lambda.repository_url
  event_bus_name = aws_cloudwatch_event_bus.event_bus.name
  s3_table_bucket_arns = [
    aws_s3tables_table_bucket.axiell_table_bucket.arn,
    aws_s3tables_table_bucket.axiell_uat_table_bucket.arn,
  ]
  manifest_bucket_name = "wellcomecollection-axiell-folio-sync-manifests"

  # The FOLIO dev server shares the catalogue VPC, so the ENIs go in the same
  # private subnets as the adapter ECS tasks above. See folio_dev_sandbox.tf.
  folio_dev_target_enabled     = local.folio_dev_target_enabled
  folio_dev_subnets            = local.folio_dev_target_enabled ? local.private_subnets : []
  folio_dev_security_group_ids = [aws_security_group.folio_sync_dev.id]

  # Live adapter syncs are off for now: nothing harvested from the live Axiell
  # system is written to any FOLIO instance. Its trigger is removed, the adapter
  # itself keeps harvesting for the catalogue pipeline, and UAT syncs carry on.
  # Setting this back to true sends live runs to production as dry runs, which
  # read from production and so need its OKAPI credentials.
  live_sync_enabled = false

  # Each Axiell system syncs to its FOLIO counterpart: live to production, UAT
  # to the test environment, where the Lambda holds every UAT run. Production
  # writes are not switched on, so the Lambda keeps every prod run dry whatever
  # dry_run_default or the event say; dry_run_default = false lets the UAT runs
  # write to test.
  folio_default_target = "prod"
  dry_run_default      = false

  # A record hard deleted in Collections is hard deleted in FOLIO too, as agreed
  # with Collection Information (wellcomecollection/platform#6833).
  hard_delete_default = true

  # The Lambda maximum. Records are written one at a time, and a timed-out run
  # is retried from the start, so the default of 300 leaves too little room
  # for the larger changesets a 15-minute harvest produces. A bulk change of
  # thousands of records still needs batching, not a longer timeout.
  lambda_timeout_seconds = 900
}
