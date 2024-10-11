module "pipeline_step" {
  source = "github.com/wellcomecollection/terraform-aws-lambda.git?ref=v1.1.1"

  name         = local.name
  package_type = "Image"
  image_uri    = "${data.aws_ecr_repository.repository.repository_url}@${data.aws_ecr_image.lambda_image.id}"
  timeout      = var.timeout
  memory_size  = var.memory_size
  description  = var.description

  environment = {
    variables = var.environment_variables
  }
}

locals {
  namespace = "catalogue-${var.pipeline_date}"

  name      = "${local.namespace}_${var.service_name}"
  image_tag = var.tag_override != "" ? var.tag_override : "env.${var.pipeline_date}"
}

data "aws_ecr_repository" "repository" {
  name = var.ecr_repository_name
}

data "aws_ecr_image" "lambda_image" {
  repository_name = data.aws_ecr_repository.repository.name
  image_tag       = local.image_tag
}

variable "tag_override" {
  type = string
  default = ""
}

variable "ecr_repository_name" {
  type = string
}

variable "service_name" {
  type = string
}

variable "description" {
  type    = string
  default = ""
}

variable "pipeline_date" {
  type = string
}


variable "environment_variables" {
  type        = map(string)
  description = "Arbitrary environment variables to give to the Lambda"
  default     = {}
}

variable "timeout" {
  default     = 600
  description = "lambda function timeout"
}

variable "memory_size" {
  default     = 1024
  description = "lambda function memory size"
}
