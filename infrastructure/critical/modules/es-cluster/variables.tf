variable "cluster_date" {
  type        = string
  description = "Date identifier used in AWS secret paths and EC deployment name (e.g. '2026-07-03')."
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
