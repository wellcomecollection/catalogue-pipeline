variable "name" {
  description = "Name of the Lambda"
}

variable "description" {
  description = "Description of the Lambda function"
}

variable "env_vars" {
  description = "Environment variables to pass to the Lambda"
  type        = map(string)
}

variable "timeout" {
  description = "The amount of time your Lambda function has to run in seconds"
}

variable "alarm_topic_arn" {
  description = "ARN of the topic where to send notification for lambda errors"
}

variable "s3_bucket" {
  description = "The S3 bucket containing the function's deployment package"
}

variable "s3_key" {
  description = "The S3 key of the function's deployment package"
}

variable "log_retention_in_days" {
  description = "The number of days to keep CloudWatch logs"
}
