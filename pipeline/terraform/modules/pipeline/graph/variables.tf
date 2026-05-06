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

variable "enable_schedule" {
  type    = bool
  default = true
}
