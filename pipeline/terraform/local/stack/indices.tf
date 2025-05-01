module "pipeline_indices" {

  source = "../../modules/pipeline_indices"

  es_works_source_index       = local.es_works_source_index
  es_works_identified_index   = local.es_works_identified_index
  es_works_denormalised_index = local.es_works_denormalised_index
  es_works_index              = local.es_works_index

  es_images_initial_index   = local.es_images_initial_index
  es_images_augmented_index = local.es_images_augmented_index
  es_images_index           = local.es_images_index

  # Path to folder containing mappings and analysis settings for Elasticsearch Index creation
  es_config_path = "${path.root}/../../../index_config"

}
