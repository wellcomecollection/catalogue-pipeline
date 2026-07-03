output "pipeline_storage_private_host" { value = var.es_private_host }
output "pipeline_storage_port" { value = var.es_port }
output "pipeline_storage_protocol" { value = var.es_protocol }

output "works_source_indices" { value = [for i in local.works_source_list : i.name] }
output "works_identified_indices" { value = [for i in local.works_identified_list : i.name] }
output "works_denormalised_indices" { value = [for i in local.works_denormalised_list : i.name] }
output "works_indexed_indices" { value = [for i in local.works_indexed_list : i.name] }
output "images_initial_indices" { value = [for i in local.images_initial_list : i.name] }
output "images_augmented_indices" { value = [for i in local.images_augmented_list : i.name] }
output "images_indexed_indices" { value = [for i in local.images_indexed_list : i.name] }
output "concepts_indexed_indices" { value = [for i in local.concepts_indexed_list : i.name] }

output "service_index_permissions" { value = local.service_index_permissions }
output "pipeline_storage_es_service_secrets" { value = local.pipeline_storage_es_service_secrets }
output "api_key_versions" { value = local.api_key_versions }
