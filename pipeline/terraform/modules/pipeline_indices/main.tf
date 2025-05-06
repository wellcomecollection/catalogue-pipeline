# Indices that are merely used as JSON stores. Unanalysed, unmapped

module "source_index" {
  source        = "../es_index"
  name          = var.es_works_source_index
  mappings_name = "empty"
  config_path   = var.es_config_path
  allow_delete  = var.allow_delete
}

module "denormalised_index" {
  source        = "../es_index"
  name          = var.es_works_denormalised_index
  mappings_name = var.index_config["works"]["denormalised"]
  config_path   = var.es_config_path
  allow_delete  = var.allow_delete
}

module "images_initial_index" {
  source        = "../es_index"
  name          = var.es_images_initial_index
  mappings_name = "empty"
  config_path   = var.es_config_path
  allow_delete  = var.allow_delete
}

module "images_augmented_index" {
  source        = "../es_index"
  name          = var.es_images_augmented_index
  mappings_name = "empty"
  config_path   = var.es_config_path
  allow_delete  = var.allow_delete
}

# Indices with their own specific mapping and analysis configurations

module "works_identified_index" {
  source        = "../es_index"
  name          = var.es_works_identified_index
  mappings_name = var.index_config["works"]["identified"]
  config_path   = var.es_config_path
  allow_delete  = var.allow_delete
}

module "works_indexed_index" {
  source        = "../es_index"
  name          = var.es_works_index
  mappings_name = var.index_config["works"]["indexed"]
  config_path   = var.es_config_path
  allow_delete  = var.allow_delete
}

# For each concepts index date, create a separate index with the corresponding mappings name
module "concepts_indexed_indexes" {
  for_each = var.index_config.concepts.indexed

  source        = "../es_index"
  name          = "${var.es_concepts_index_prefix}-${each.key}"
  mappings_name = each.value
  config_path   = var.es_config_path
  allow_delete  = var.allow_delete
}

module "images_indexed_index" {
  source        = "../es_index"
  name          = var.es_images_index
  mappings_name = var.index_config["images"]["indexed"]
  # Images contain a superset of the fields in Works
  # and share the same analysis settings for them
  # The images-specific fields do not use any extra custom analysis.
  analysis_name = var.index_config["images"]["works_analysis"]
  config_path   = var.es_config_path
  allow_delete  = var.allow_delete
}
