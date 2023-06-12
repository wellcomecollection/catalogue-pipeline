
variable "name" {
  type = string
}

variable "mappings_name" {
  type = string
}

variable "analysis_name" {
  type        = string
  default     = ""
  description = "Defaults to match mappings_name"
}

variable "config_path" {
  type    = string
  default = ""
}


variable "connection" {
  type = object({
    username  = string
    password  = string
    endpoints = list(string)
  })
}

