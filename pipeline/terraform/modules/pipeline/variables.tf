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



variable "elastic_outputs" {
  description = "Outputs from the elastic module (indices, API keys, connection details)."
  type        = any
}

variable "ami_id" {
  type        = string
  description = "AMI to use for the ECS EC2 cluster host"
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
