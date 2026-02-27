variable "environment" {
  type        = map(string)
  description = "A map of environment variables to pass to the container"
}

variable "image" {
  type        = string
  description = "The container image to use for the container"
}

variable "cpu" {
  type        = number
  description = "The number of CPU units to reserve for the container"
}

variable "memory" {
  type        = number
  description = "The amount of memory to reserve for the container"
}

variable "task_name" {
  type        = string
  description = "The name of the task"
}

variable "secret_env_vars" {
  type        = map(string)
  default     = {}
  description = "A map of secret environment variables (name => secret ARN or secret name)"
}
