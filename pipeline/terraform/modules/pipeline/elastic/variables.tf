variable "pipeline_date" { type = string }
variable "es_cluster_deployment_template" { type = string }
variable "es_node_count" { type = number }
variable "es_memory" { type = string }
variable "network_config" {
  description = "Object containing network settings incl. traffic_filters"
  type        = any
}
variable "monitoring_config" {
  description = "Monitoring configuration incl logging_cluster_id"
  type        = any
}
variable "allow_delete_indices" { type = bool }
variable "index_config" { type = map(object({
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
})) }

variable "catalogue_account_services" { type = set(string) }
variable "version_regex" { type = string }