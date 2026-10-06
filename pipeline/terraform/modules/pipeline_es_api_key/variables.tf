variable "read_from" {
  type        = list(string)
  default     = []
  description = "List of indices this API key allows reading from"
}

variable "read_privileges" {
  type        = list(string)
  default     = ["read"]
  description = "Index privileges granted on the read_from indices"
}

variable "cluster_privileges" {
  type        = list(string)
  default     = []
  description = "Cluster privileges added to the read role descriptor, eg. ['monitor']; empty omits the cluster entry"
}

variable "write_to" {
  type        = list(string)
  default     = []
  description = "List of indices this API key allows writing to"
}

variable "name" {
  type = string
}

variable "pipeline_date" {
  type        = string
  description = "Pipeline date used in service names, eg. 'merger-2025-10-02'"
}

variable "expose_to_catalogue" {
  type    = bool
  default = false
}