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
  default     = "4g"
}

variable "es_cluster_deployment_template" {
  type        = string
  description = "Which hardware profile should the cluster use? Choose from https://www.elastic.co/guide/en/cloud/current/ec-regions-templates-instances.html#eu-west-1"
  default     = "aws-cpu-optimized-arm"
}

locals {
  es_memory = var.reindexing_state.scale_up_elastic_cluster ? "30g" : var.es_cluster_memory

  # When we're reindexing, this cluster isn't depended on for anything.
  # It's ephemeral data (and at 30GB of memory, expensive).
  #
  # Once we stop reindexing and make the pipeline live, we want it to be
  # highly available, because it's serving API traffic.
  es_node_count = var.reindexing_state.scale_up_elastic_cluster ? 2 : var.es_cluster_nodes
}

variable "index_config" {
  type = map(object({
    works = optional(object({
      source       = optional(string)
      identified   = optional(string)
      denormalised = optional(string)
      indexed      = optional(string)
    }), {})
    images = optional(object({
      initial   = optional(string)
      augmented = optional(string)
      indexed   = optional(string)
    }), {})
    concepts = optional(object({
      indexed = optional(string)
    }), {})
  }))
  description = "Index configuration keyed by pipeline date. Omit a field to skip creation of that index."
}

variable "allow_delete_indices" {
  type    = bool
  default = false
}

variable "ami_id" {
  type        = string
  description = "AMI to use for the ECS EC2 cluster host"
}

variable "version_regex" {
  type = string
}


variable "graph_index_dates" {
  type = object({
    merged    = string
    augmented = string
    works     = string
    concepts  = string
    images    = string
  })
}

variable "enable_image_inferrer_schedule" {
  type        = bool
  default     = false
  description = "Enable the scheduled image-inferrer state machine. Ships disabled until the new path is validated and we cut over from the SQS-driven service."
}

variable "image_inferrer_augmented_index_date" {
  type        = string
  default     = ""
  description = <<-EOT
    Augmented index date the Python image-inferrer state machine writes to. Empty (the default) falls
    back to `graph_index_dates.augmented` — i.e. the production augmented index the images ingestor
    reads — which is the steady-state cutover target. Set explicitly only to write a separate shadow
    index (e.g. `2026-06-15`) for isolated comparison runs. A matching `index_config` entry must exist
    (see the 2025-10-02 root).
  EOT
}

variable "image_inferrer_initial_index_date" {
  type        = string
  default     = ""
  description = <<-EOT
    Initial-images index the merger writes and both inferrers read. Empty (the default) falls back to
    `var.pipeline_date`, which is the steady-state once a fresh pipeline's images-initial is created
    with a mapping that indexes `modifiedTime`. Set explicitly during the in-place migration on an
    existing pipeline whose live images-initial uses the "empty"/dynamic:false mapping (where
    `modifiedTime` is unqueryable): point it at a modifiedTime-mapped index (e.g. `2026-06-15`) that the
    merger is moved onto. A matching `index_config` entry must exist (see the 2025-10-02 root).
  EOT
}

variable "graph_images_augmented_index_date" {
  type        = string
  default     = ""
  description = <<-EOT
    Augmented-images index that the graph subsystem READ-path (graph extractor, images ingestor,
    incremental remover) uses as its source. Empty (the default) falls back to
    `graph_index_dates.augmented`, keeping the read-path and the old Scala inferrer's write target
    (`local.es_images_augmented_index`, also derived from `graph_index_dates.augmented`) on the same
    index. Set explicitly to decouple them — i.e. to cut the read-path over to the new Python inferrer's
    output (e.g. `2026-06-15`) while the old SQS-driven service keeps writing
    `graph_index_dates.augmented` as an untouched, live fallback (so a rollback is just reverting this
    var). The target index must be at full coverage before the switch.
  EOT
}

variable "image_inferrer_max_concurrency" {
  type        = number
  default     = 10
  description = <<-EOT
    Single source of truth for image-inference parallelism (when not reindexing). Drives BOTH the
    inferrer EC2 capacity provider's `max_instances` AND the state machine Map's `MaxConcurrency`, so
    the Map can never fan out more concurrent tasks than the ASG can place. Each task fills one
    c5.xlarge (~4096 CPU), so one instance == one task and the two values stay equal. The ASG scales
    to 0 when idle, so this is only a ceiling, not a running cost. (During a full reindex,
    `reindexing_state.scale_up_tasks` overrides both to the larger fixed size.)
  EOT
}
