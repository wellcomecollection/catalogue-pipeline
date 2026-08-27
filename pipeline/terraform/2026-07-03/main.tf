module "pipeline" {
  source = "../modules/pipeline_new"

  reindexing_state = {
    listen_to_reindexer = true
    scale_up_tasks      = false
    # ~$135/day flat: applied last before the reindex starts, reverted first
    # after the comparison signs off.
    scale_up_matcher_db = true
  }

  index_dates = {
    source     = "2026-07-03"
    identified = "2026-07-03"
    merged     = "2026-07-03"
    initial    = "2026-07-03"
    augmented  = "2026-07-03"
    works      = "2026-07-03"
    concepts   = "2026-07-03"
    images     = "2026-07-03"
  }

  # Base AMI for ECS instances
  ami_id = "resolve:ssm:arn:aws:ssm:eu-west-1:760097843905:parameter/imagebuilder/weco-al2023-ecs-optimised-x86_64/latest"

  enable_adapter_transformer_trigger           = true
  disable_calm_transformer_topic_subscriptions = true
  enable_id_minter_schedule                    = true
  enable_graph_pipeline_schedule               = true
  enable_image_inferrer_schedule               = true

  pipeline_date = local.pipeline_date // namespaces services
  graph_date    = "2026-07-03"        // namespaces graph database
  rds_id_minter = "2026-07-03"        // id-minter RDS cluster to use
  release_label = local.pipeline_date

  elastic = module.elastic

  providers = {
    aws           = aws
    aws.catalogue = aws.catalogue
  }
}
