variable "es_works_source_index" {
  type = string
}

variable "es_works_identified_index" {
  type = string
}
variable "es_works_denormalised_index" {
  type = string
}
variable "es_works_index" {
  type = string
}
variable "es_concepts_index_prefix" {
  type = string
}
variable "es_images_initial_index" {
  type = string
}
variable "es_images_augmented_index" {
  type = string
}
variable "es_images_index" {
  type = string
}
variable "es_config_path" {
  type = string
}

variable "index_config" {
  type = object({
    works = object({
      identified = string
      merged     = string
      indexed    = string
    })

    concepts = object({
      indexed = map(string)
    })

    images = object({
      indexed        = string
      works_analysis = string
    })
  })
}

variable "allow_delete" {
  type    = bool
  default = false
}
