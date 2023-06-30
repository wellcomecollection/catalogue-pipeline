
variable "es_works_source_index" {
  type = string
}
variable "es_works_merged_index" {
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

variable "connection" {
  type = object({
    username  = string
    password  = string
    endpoints = list(string)
  })
}

variable "index_config" {
  type = object({
    works = object({
      identified = string
      merged     = string
      indexed    = string
    })

    images = object({
      indexed        = string
      works_analysis = string
    })
  })
}
