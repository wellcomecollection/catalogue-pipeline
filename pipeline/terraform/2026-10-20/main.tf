module "pipeline" {
  source = "../modules/pipeline"

  # The FOLIO pipeline environment (wellcomecollection/platform#6534): its own
  # registry, graph and serverless Elasticsearch, so FOLIO ids are never minted
  # into the registry that production uses.
  reindexing_state = {
    listen_to_reindexer = false
    scale_up_tasks      = false
    scale_up_matcher_db = false
  }

  index_dates = {
    source     = "2026-10-20"
    identified = "2026-10-20"
    merged     = "2026-10-20"
    initial    = "2026-10-20"
    augmented  = "2026-10-20"
    works      = "2026-10-20"
    concepts   = "2026-10-20"
    images     = "2026-10-20"
  }

  # Base AMI for ECS instances
  ami_id = "resolve:ssm:arn:aws:ssm:eu-west-1:760097843905:parameter/imagebuilder/weco-al2023-ecs-optimised-x86_64/latest"

  enable_adapter_transformer_trigger           = true
  disable_calm_transformer_topic_subscriptions = true
  enable_id_minter_schedule                    = true
  enable_graph_pipeline_schedule               = true
  enable_image_inferrer_schedule               = true

  axiell_collection_path_source = "part_of"

  # One bulk request per SQS batch instead of one per matcher result: serverless
  # charges about 260 ms per request whatever its size (wellcomecollection/platform#6714).
  merger_batch_indexing = true

  pipeline_date = local.pipeline_date // namespaces services
  graph_date    = "2026-10-20"        // namespaces graph database
  rds_id_minter = "2026-10-20"        // a copy of production's registry from 2026-10-20 04:00
  release_label = local.pipeline_date

  elastic                        = module.elastic
  elastic_vpce_security_group_id = data.terraform_remote_state.catalogue_infra_critical.outputs.es_serverless_vpce_security_group_id

  providers = {
    aws           = aws
    aws.catalogue = aws.catalogue
  }
}
