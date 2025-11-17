variable "state_machine_arn" {
  description = "ARN of the Step Functions state machine to monitor."
  type        = string
}

variable "alarm_name_prefix" {
  description = "Prefix used when constructing alarm names."
  type        = string
}

variable "alarm_name_suffix" {
  description = "Suffix appended to each alarm name (optional)."
  type        = string
  default     = ""
}

variable "default_alarm_configuration" {
  description = "Map containing default configuration values shared by all alarms (alarm_actions, period, actions_enabled)."
  type        = map(any)
  default     = {}
}

variable "alarm_overrides" {
  description = "Per-alarm override map keyed by alarm type (aborted, failed, timed_out) allowing custom alarm_actions, period, actions_enabled, and alarm_description."
  type        = map(any)
  default     = {}
}
