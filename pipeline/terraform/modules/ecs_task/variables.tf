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

variable "ephemeral_storage_size" {
  type        = number
  default     = null
  description = "The size of ephemeral storage (in GiB) for the task. Minimum 21, max 200. Null uses the Fargate default (20 GiB)."
}

# Sidecar image tags are set centrally in platform-infrastructure/sidecars/terraform.
# Pipelines have no stage tier of their own, so they follow prod.
variable "sidecar_tier" {
  type    = string
  default = "prod"

  validation {
    condition     = contains(["stage", "prod"], var.sidecar_tier)
    error_message = "sidecar_tier must be stage or prod."
  }
}

# Pins this service to a tag instead of the tier's parameter.
variable "fluentbit_tag_override" {
  type    = string
  default = null
}
