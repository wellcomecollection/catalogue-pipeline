variable "name" {
  type = string
}

variable "description" {
  type = string
}

variable "s3_bucket" {
  type = string
}

variable "s3_key" {
  type = string
}

variable "env_vars" {
  type = map(string)
}

variable "timeout" {
  type    = number
  default = 3
}

variable "schedule_interval" {
  type = string
}

variable "events_enabled" {
  type    = bool
  default = true
}
