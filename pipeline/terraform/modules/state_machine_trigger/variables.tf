variable "name" {
  description = "Base name for the trigger resources (rule, role, policy)"
  type        = string
}

variable "event_bus_name" {
  description = "Name of the EventBridge event bus to attach the rule to"
  type        = string
}

variable "state_machine_arn" {
  description = "ARN of the Step Functions state machine to trigger"
  type        = string
}

variable "event_pattern" {
  description = "JSON encodable object representing the EventBridge event pattern"
  type        = any
}

variable "input_template" {
  description = "Input template for optional input transformer (if null, no transformer is used)"
  type        = string
  default     = null
}

variable "input_paths" {
  description = "Map of input paths for input transformer (if empty, none used)"
  type        = map(string)
  default     = {}
}
