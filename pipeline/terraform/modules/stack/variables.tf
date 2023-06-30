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

# This allows us to choose the size of the Elasticsearch cluster.
#
# There are three sizes we can choose from:
#
#     * 58GB x 2 = a 2-node instance with lots of memory, when we know we're
#       going to be sending the cluster lots of traffic.
#
#       This is what we use when reindexing.  It's auto-selected if you
#       enable `scale_up_elastic_cluster` = true.
#
#       Cost: ~$130/day
#
#     * 8GB x 3 = a 3-node, HA instance with enough memory to serve
#       prod API traffic.
#
#       Cost: ~$30/day
#
#     * 2GB x 2 = a 2-node instance with just enough memory and storage,
#       when we want to keep a cluster around (e.g. for investigation, or
#       if we want to keep it just-in-case we need a rollback), but don't
#       need it ready to serve traffic immediately.
#
#       Cost: ~$6.50/day
#
variable "es_cluster_size" {
  description = "How big should the Elastic cluster be?"
  default     = "3x8g"

  validation {
    condition     = contains(["2x58g", "3x8g", "3x2g"], var.es_cluster_size)
    error_message = "Cooldown period should be one of: 2x58g, 3x8g, 3x2g."
  }
}

locals {
  es_memory_lookup = {
    "2x58g" : "58g"
    "3x8g" : "8g"
    "3x2g" : "2g"
  }

  es_node_lookup = {
    "2x58g" : 2
    "3x8g" : 3
    "3x2g" : 3
  }

  es_memory = var.reindexing_state.scale_up_elastic_cluster ? "58g" : local.es_memory_lookup[var.es_cluster_size]

  # When we're reindexing, this cluster isn't depended on for anything.
  # It's ephemeral data (and at 58GB of memory, expensive).
  #
  # Once we stop reindexing and make the pipeline live, we want it to be
  # highly available, because it's serving API traffic.
  es_node_count = var.reindexing_state.scale_up_elastic_cluster ? 2 : local.es_node_lookup[var.es_cluster_size]
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
