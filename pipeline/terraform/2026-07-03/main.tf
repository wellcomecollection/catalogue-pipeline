module "pipeline" {
  source = "../modules/pipeline_new"

  # Quiesced for the switchover clear (wellcomecollection/platform#6718): nothing
  # downstream of works-source runs until phase 7 re-enables it after the
  # in-freeze id-minter respin. The legacy transformers keep writing works-source.
  reindexing_state = {
    listen_to_reindexer = false
    scale_up_tasks      = true # kept from round 3 for the reindex that follows
    scale_up_matcher_db = false
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

  enable_adapter_transformer_trigger           = false
  disable_calm_transformer_topic_subscriptions = true
  enable_id_minter_schedule                    = false
  enable_graph_pipeline_schedule               = false
  enable_image_inferrer_schedule               = false

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
