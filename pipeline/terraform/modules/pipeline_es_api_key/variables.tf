variable "read_from" {
  type        = list(string)
  default     = []
  description = "List of indices this API key allows reading from"
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