variable "pipeline_date" {
  type = string
}

variable "index_dates" {
  type = object({
    merged    = string
    augmented = string
    works     = string
    concepts  = string
    images    = string
  })
}

variable "es_cluster_host" {
  type = string
}

variable "es_cluster_port" {
  type = string
}

variable "es_cluster_protocol" {
  type = string
}

variable "es_secrets" {
  type = object({
    concepts_ingestor = string
    works_ingestor    = string
    images_ingestor   = string
    graph_extractor   = string
  })
}

variable "ecs_cluster_arn" {
  type = string
}

variable "graph_date" {
  type        = string
  description = "Graph date identifying the Neptune cluster for this pipeline run (YYYY-MM-DD, or 'dev')."

  validation {
    condition     = var.graph_date == "dev" || can(regex("^\\d{4}-\\d{2}-\\d{2}$", var.graph_date))
    error_message = "graph_date must be a date (YYYY-MM-DD) or 'dev'."
  }
}

variable "enable_schedule" {
  type    = bool
  default = true
}
