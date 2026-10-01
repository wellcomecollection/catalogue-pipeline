module "pipeline" {
  source = "../modules/pipeline_new"

  # Builds Axiell archive trees from the 982 parent link (wellcomecollection/platform#6725).
  # Scaled up for the full reindex; scale the matcher DB back down once the works funnel closes.
  reindexing_state = {
    listen_to_reindexer = true
    scale_up_tasks      = true
    scale_up_matcher_db = true
  }

  index_dates = {
    source     = "2026-09-30"
    identified = "2026-09-30"
    merged     = "2026-09-30"
    initial    = "2026-09-30"
    augmented  = "2026-09-30"
    works      = "2026-09-30"
    concepts   = "2026-09-30"
    images     = "2026-09-30"
  }

  # Base AMI for ECS instances
  ami_id = "resolve:ssm:arn:aws:ssm:eu-west-1:760097843905:parameter/imagebuilder/weco-al2023-ecs-optimised-x86_64/latest"

  enable_adapter_transformer_trigger           = true
  disable_calm_transformer_topic_subscriptions = true
  enable_id_minter_schedule                    = true
  enable_graph_pipeline_schedule               = true
  enable_image_inferrer_schedule               = true

  axiell_collection_path_source = "part_of"

  pipeline_date = local.pipeline_date // namespaces services
  graph_date    = "2026-09-30"        // namespaces graph database
  rds_id_minter = "2026-07-03"        // production's registry, so canonical ids carry over
  release_label = local.pipeline_date

  elastic = module.elastic

  providers = {
    aws           = aws
    aws.catalogue = aws.catalogue
  }
}
