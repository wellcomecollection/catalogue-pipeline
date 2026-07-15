variable "pipeline_date" {
  // namespace for the pipeline services
  type = string
}

variable "graph_date" {
  type        = string
  description = "Graph date identifying the Neptune cluster for this pipeline run."
}

variable "rds_id_minter" {
  type        = string
  description = "Id_minter RDS cluster to use for this pipeline."
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
    listen_to_reindexer = bool
    scale_up_tasks      = bool
    scale_up_matcher_db = bool
  })
}

variable "release_label" {
  type = string
}


variable "elastic" {
  description = "Outputs from the elastic module (indices, API keys, connection details)."
  type        = any
}

variable "ami_id" {
  type        = string
  description = "AMI to use for the ECS EC2 cluster host"
}

variable "index_dates" {
  type = object({
    source     = string
    identified = string
    merged     = string
    initial    = string
    augmented  = string
    works      = string
    concepts   = string
    images     = string
  })
}

variable "enable_image_inferrer_schedule" {
  type        = bool
  default     = true
  description = "Whether the scheduled image-inferrer state machine is enabled. Defaults to true, since it is the sole image inferrer. Set to false as a kill-switch to pause scheduled inference, e.g. during an incident or a large reindex."
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
