variable "tag_override" {
  type = string
  default = ""
}

variable "ecr_repository_name" {
  type = string
}

variable "service_name" {
  type = string
}

variable "description" {
  type    = string
  default = ""
}

variable "pipeline_date" {
  type = string
}


variable "environment_variables" {
  type        = map(string)
  description = "Arbitrary environment variables to give to the Lambda"
  default     = {}
}

variable "timeout" {
  default     = 600
  description = "lambda function timeout"
}

variable "memory_size" {
  default     = 1024
  description = "lambda function memory size"
}
