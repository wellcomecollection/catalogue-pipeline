variable "name" {
  description = "Name of the Lambda"
  type        = string
}

variable "handler" {
  type = string
}

variable "description" {
  description = "Description of the Lambda function"

  type = string
}

variable "env_vars" {
  description = "Environment variables to pass to the Lambda"

  type = map(string)
}

variable "timeout" {
  description = "The amount of time your Lambda function has to run in seconds"

  type = string
}

variable "alarm_topic_arn" {
  description = "ARN of the topic where to send notification for lambda errors"

  type = string
}

variable "s3_bucket" {
  description = "The S3 bucket containing the function's deployment package"

  type = string
}

variable "s3_key" {
  description = "The S3 key of the function's deployment package"

  type = string
}

variable "log_retention_in_days" {
  description = "The number of days to keep CloudWatch logs"

  type = string
}
