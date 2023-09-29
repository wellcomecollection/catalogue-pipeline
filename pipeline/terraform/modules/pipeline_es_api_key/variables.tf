variable "read_from" {
  type    = list(string)
  default = []
}

variable "write_to" {
  type    = list(string)
  default = []
}

variable "name" {
  type = string
}

variable "pipeline_date" {
  type = string
}

variable "expose_to_catalogue" {
  type    = bool
  default = false
}