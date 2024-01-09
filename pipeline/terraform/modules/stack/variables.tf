variable "pipeline_date" {
  type = string
}

variable "min_capacity" {
  type    = number
  default = 0
}

variable "max_capacity" {
  type        = number
  default     = 12
  description = "The max capacity of every ECS service will be less than or equal to this value"
}

variable "reindexing_state" {
  type = object({
    listen_to_reindexer      = bool
    scale_up_tasks           = bool
    scale_up_elastic_cluster = bool
    scale_up_id_minter_db    = bool
    scale_up_matcher_db      = bool
  })
}

variable "release_label" {
  type = string
}

variable "es_cluster_nodes" {
  type        = number
  description = "How many nodes should the cluster have?"
  default     = 3
}

variable "es_cluster_memory" {
  type        = string
  description = "How much memory should the cluster have?"
  default     = "8g"
}

variable "es_cluster_deployment_template" {
  type        = string
  description = "Which hardware profile should the cluster use? Choose from https://www.elastic.co/guide/en/cloud/current/ec-regions-templates-instances.html#eu-west-1"
  default     = "aws-io-optimized-v2"
}

locals {
  es_memory = var.reindexing_state.scale_up_elastic_cluster ? "58g" : var.es_cluster_memory

  # When we're reindexing, this cluster isn't depended on for anything.
  # It's ephemeral data (and at 58GB of memory, expensive).
  #
  # Once we stop reindexing and make the pipeline live, we want it to be
  # highly available, because it's serving API traffic.
  es_node_count = var.reindexing_state.scale_up_elastic_cluster ? 2 : var.es_cluster_nodes

  # aws-storage-optimized uses the same instances as the old (deprecated) default we used of "aws-io-optimized-v2"
  # CPU-optimized might be best for reindexing as well but now (3/1/2024) we are just looking at search performance
  es_deployment_template_id = var.reindexing_state.scale_up_elastic_cluster ? "aws-storage-optimized" : var.es_cluster_deployment_template
}

variable "index_config" {
  type = object({
    works = object({
      identified = string
      merged     = string
      indexed    = string
    })

    images = object({
      indexed        = string
      works_analysis = string
    })
  })
}

variable "allow_delete_indices" {
  type    = bool
  default = false
}
