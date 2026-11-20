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
  description = "Per-alarm override map keyed by alarm type (aborted, failed, timed_out) allowing custom alarm_actions, period, actions_enabled, alarm_description, and severity."
  type        = map(any)
  default     = {}
}

# Alarm tags and description prefix from the alert routing ADR (wellcomecollection/docs adr/alert_routing.md)
variable "alarm_component" {
  description = "Backplane Component name (metadata.name in manifest.yml) for the backplane:component tag and description prefix."
  type        = string
  default     = "catalogue-graph"
}

variable "alarm_repository" {
  description = "Owning repository, for the github.com/project-slug tag."
  type        = string
  default     = "wellcomecollection/catalogue-pipeline"
}
