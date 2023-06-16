
# Indices that are merely used as JSON stores. Unanalysed, unmapped

module "source_index" {
  source        = "../es_index"
  name          = var.es_works_source_index
  mappings_name = "empty"
  config_path   = var.es_config_path
  connection    = var.connection
}

module "denormalised_index" {
  source        = "../es_index"
  name          = var.es_works_denormalised_index
  mappings_name = "empty"
  config_path   = var.es_config_path
  connection    = var.connection
}

module "images_initial_index" {
  source        = "../es_index"
  name          = var.es_images_initial_index
  mappings_name = "empty"
  config_path   = var.es_config_path
  connection    = var.connection
}

module "images_augmented_index" {
  source        = "../es_index"
  name          = var.es_images_augmented_index
  mappings_name = "empty"
  config_path   = var.es_config_path
  connection    = var.connection
}

# Indices with their own specific mapping and analysis configurations

module "works_identified_index" {
  source        = "../es_index"
  name          = var.es_works_identified_index
  mappings_name = "works_identified.2023-05-26"
  config_path   = var.es_config_path
  connection    = var.connection
}


module "works_merged_index" {
  source        = "../es_index"
  name          = var.es_works_merged_index
  mappings_name = "works_merged.2023-05-26"
  config_path   = var.es_config_path
  connection    = var.connection
}

module "works_indexed_index" {
  source        = "../es_index"
  name          = var.es_works_index
  mappings_name = "works_indexed.2023-05-26"
  config_path   = var.es_config_path
  connection    = var.connection
}

module "images_indexed_index" {
  source        = "../es_index"
  name          = var.es_images_index
  mappings_name = "images_indexed.2023-05-26"
  # Images contain a superset of the fields in Works
  # and share the same analysis settings for them
  # The images-specific fields do not use any extra custom analysis.
  analysis_name = "works_indexed.2023-05-26"
  config_path   = var.es_config_path
  connection    = var.connection
}

