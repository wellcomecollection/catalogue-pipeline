output "pipeline_storage_elastic_id" { value = local.pipeline_storage_elastic_id }
output "pipeline_storage_elastic_region" { value = local.pipeline_storage_elastic_region }
output "pipeline_storage_public_host" { value = local.pipeline_storage_public_host }
output "pipeline_storage_private_host" { value = local.pipeline_storage_private_host }
output "pipeline_storage_protocol" { value = local.pipeline_storage_protocol }
output "pipeline_storage_port" { value = local.pipeline_storage_port }
output "es_username" { value = ec_deployment.pipeline.elasticsearch_username }
output "es_password" {
  value     = ec_deployment.pipeline.elasticsearch_password
  sensitive = true
}

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
