variable "name" {
  type        = string
  description = "Underscored service name, e.g. `image_inferrer`. Names the state machine (`pipeline-<date>_<name>`); the hyphenated form names the schedule and scheduler role."
}

variable "pipeline_date" {
  type = string
}

variable "comment" {
  type        = string
  description = "Comment on the rendered state machine definition."
}

variable "ecr_repository_name" {
  type        = string
  description = "ECR repository holding the unified pipeline Lambda image the find-work Lambda runs from."
}

variable "find_work_lambda" {
  type = object({
    service_name = string
    description  = string
    command      = list(string)
    memory_size  = optional(number, 1024)
    timeout      = optional(number, 300)
  })
  description = "The work-discovery Lambda: an entrypoint in the unified pipeline image that scans for ids in scope and partitions them to S3."
}

variable "vpc_config" {
  type = object({
    subnet_ids         = list(string)
    security_group_ids = list(string)
  })
}

variable "find_work_secret_read_policy_json" {
  type        = string
  description = "IAM policy granting the find-work Lambda read access to its ES credentials."
}

variable "partition_s3_arns" {
  type        = list(string)
  description = "S3 object ARNs the find-work Lambda writes partition files to."
}

variable "static_event_fields" {
  type        = any
  default     = {}
  description = "Extra fields merged into every constructed event alongside pipeline_date (e.g. index_dates, graph_date)."
}

variable "max_concurrency" {
  type        = number
  description = "Map MaxConcurrency: the work-in-progress ceiling. Pin to the worker's real capacity (ASG instances, DB connection budget)."
}

variable "worker_state_name" {
  type        = string
  description = "Name of the injected worker state."
}

variable "worker_state" {
  type        = any
  description = "ASL Task state that processes one partition ref, passed without Catch or End (the module appends both)."
}

variable "worker_lambda_arns" {
  type        = list(string)
  default     = []
  description = "Lambda ARNs the state machine invokes as workers (empty for ECS-based workers)."
}

variable "state_machine_policies" {
  type        = map(string)
  default     = {}
  description = "Extra IAM policy documents to attach to the state machine role (e.g. runTask on the worker's ECS task)."
}

variable "tolerate_partition_failures" {
  type        = bool
  description = "Tolerate failed partitions. Only safe when a later window re-covers the same records; otherwise the execution fails once every partition has finished."
}

variable "schedule" {
  type = object({
    cron    = string
    enabled = bool
  })
}

variable "alarm_name_prefix" {
  type        = string
  default     = null
  description = "Defaults to the hyphenated service name; set only when the alarm naming must differ from it."
}

variable "alarm_topic_arn" {
  type = string
}
