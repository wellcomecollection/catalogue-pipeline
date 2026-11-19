variable "pipeline_date" { type = string }

variable "es_cluster" {
  type = object({
    https_endpoint     = string
    username           = string
    password           = string
    private_host       = string
    public_host        = string
    port               = string
    protocol           = string
    read_only_username = optional(string)
    read_only_password = optional(string)
  })
  sensitive   = true
  description = "Elasticsearch cluster connection details (from infrastructure/critical outputs)."

  validation {
    condition     = (var.es_cluster.read_only_username == null) == (var.es_cluster.read_only_password == null)
    error_message = "read_only_username and read_only_password must be set together or both left null."
  }
}

variable "allow_delete_indices" {
  type    = bool
  default = false
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
  description = "Index configuration keyed by pipeline date."
}

variable "catalogue_account_services" {
  type    = set(string)
  default = []
}

variable "batch_merger_index_date" {
  type        = string
  default     = null
  description = "The date of the works-denormalised and images-initial indices the batch merger writes to, which index_config must define. Gives the batch merger an API key that writes to those two only."
}
