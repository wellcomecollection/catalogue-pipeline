variable "pipeline_date" { type = string }

variable "es_cluster_date" { type = string }

variable "es_endpoint" {
  type        = string
  description = "Elasticsearch HTTPS endpoint (e.g. https://xxx.eu-west-1.aws.found.io:9243)"
}

variable "es_username" {
  type      = string
  sensitive = true
}

variable "es_password" {
  type      = string
  sensitive = true
}

variable "es_private_host" {
  type        = string
  description = "Private VPC endpoint hostname for the cluster"
}

variable "es_port" {
  type = string
}

variable "es_protocol" {
  type = string
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
