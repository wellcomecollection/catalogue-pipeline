variable "namespace" {
  type = string
}

variable "resource_type" {
  type = string
}

variable "windows_topic_arns" {
  description = "A list of topic ARNs to listen to for new windows"
  type        = list(string)
}

variable "lambda_error_alarm_arn" {
  type = string
}

variable "sierra_fields" {
  type = string
}
