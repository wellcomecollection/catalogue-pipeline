variable "task_name" {
  type        = string
  description = "Name of the task and of the inference_manager container the state machine overrides."
}

# --- Manager (inference_manager) container --------------------------------- #

variable "manager_image" {
  type        = string
  description = "Container image for the Python inference_manager (the unified_pipeline_task image)."
}

variable "manager_cpu" {
  type = number
}

variable "manager_memory" {
  type = number
}

variable "manager_env_vars" {
  type    = map(string)
  default = {}
}

variable "manager_mount_points" {
  type = list(object({
    containerPath = string
    sourceVolume  = string
  }))
  default = []
}

# --- Inferrer sidecars ----------------------------------------------------- #

variable "apps" {
  type = map(object({
    image           = string
    env_vars        = map(string)
    secret_env_vars = map(string)
    cpu             = number
    memory          = number
    healthcheck     = any
    mount_points = list(object({
      containerPath = string
      sourceVolume  = string
    }))
  }))
}

variable "volumes" {
  type = list(object({
    name      = string
    host_path = string
  }))
  default = []
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
