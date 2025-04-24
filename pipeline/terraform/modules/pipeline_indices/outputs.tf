output "index_names" {
  value = {
    source           = module.source_index.name
    denormalised     = module.denormalised_index.name
    images_initial   = module.images_initial_index.name
    images_augmented = module.images_augmented_index.name
    images_indexed   = module.images_indexed_index.name
    works_identified = module.works_identified_index.name
    works_merged     = module.works_merged_index.name
    works_indexed    = module.works_indexed_index.name
  }
}
