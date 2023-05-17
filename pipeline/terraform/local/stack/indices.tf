
/*
Certain indices are merely used as JSON stores, and do not require any mappings or analysis.
*/
module "source_index" {
  source        = "../modules/es_index"
  name          = local.es_works_source_index
  mappings_name = "empty"
}

module "denormalised_index" {
  source        = "../modules/es_index"
  name          = local.es_works_denormalised_index
  mappings_name = "empty"
}

module "images_initial_index" {
  source        = "../modules/es_index"
  name          = local.es_images_initial_index
  mappings_name = "empty"
}

module "images_augmented_index" {
  source        = "../modules/es_index"
  name          = local.es_images_augmented_index
  mappings_name = "empty"
}


module "works_identified_index" {
  source = "../modules/es_index"
  name = local.es_works_identified_index
  mappings_name  = "works_identified.v1"
}


module "works_merged_index" {
  source        = "../modules/es_index"
  name          = local.es_works_merged_index
  mappings_name = "works_merged.v1"
}

module "works_indexed_index" {
  source = "../modules/es_index"
  name = local.es_works_index
  mappings_name  = "works_indexed.v1"
}

module "images_indexed_index" {
  source = "../modules/es_index"
  name = local.es_images_index
  mappings_name  = "images_indexed.v1"
  // Images contain a superset of the fields in Works
  // and share the same analysis settings for them
  // The images-specific fields do not use any extra custom analysis.
  analysis_name  = "works_indexed.v1"
}


