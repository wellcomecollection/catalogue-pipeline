variable "cluster_name" {
  type        = string
  description = "Short identifier used in AWS secret paths and EC deployment name (e.g. 'pipeline_storage_v1')."
}

variable "deployment_template" {
  type    = string
  default = "aws-cpu-optimized-arm"
}

variable "node_count" {
  type    = number
  default = 3
}

variable "memory" {
  type    = string
  default = "4g"
}

variable "version_regex" {
  type    = string
  default = "9.1.?"
}

variable "traffic_filter_ids" {
  type = list(string)
}

variable "logging_cluster_id" {
  type = string
}
