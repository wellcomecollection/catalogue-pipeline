variable "pipeline_date" { // nammespace for the pipeline services
  type = string
}

variable "es_cluster_date" { // the es cluster that the pipeline services use, eg. "es-cluster-2026-07-03"
  type = string
}

variable "enabled_services" {
  type        = set(string)
  description = "Set of services to create in this stack. Omit a service to skip it entirely."
  default = [
    "transformers",
    "id_minter",
    "matcher",
    "merger",
    "image_inferrer",
    "graph_pipeline",
  ]
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



variable "elastic_outputs" {
  description = "Outputs from the elastic module (indices, API keys, connection details)."
  type        = any
}

variable "ami_id" {
  type        = string
  description = "AMI to use for the ECS EC2 cluster host"
}

variable "version_regex" {
  type = string
}


variable "graph_date" {
  type        = string
  description = "Graph date identifying the Neptune cluster for this pipeline run. Empty string = legacy pre-dated prod cluster."
  default     = ""
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
  default     = true
  description = "Whether the scheduled image-inferrer state machine is enabled. Defaults to true, since it is the sole image inferrer. Set to false as a kill-switch to pause scheduled inference, e.g. during an incident or a large reindex."
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
