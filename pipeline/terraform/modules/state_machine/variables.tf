variable "name" {
  type = string
}

variable "state_machine_definition" {}

variable "invokable_lambda_arns" {
  type    = list(string)
  default = []
}

variable "state_machine_iam_policy" {
  type    = string
  default = null
}