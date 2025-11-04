variable "name" {
  type = string
}

variable "state_machine_definition" {}

variable "invokable_lambda_arns" {
  type    = list(string)
  default = []
}

variable "invokable_state_machine_arns" {
  type    = list(string)
  default = []
}

# TODO: Remove this variable in favour of policies_to_attach
variable "state_machine_iam_policy" {
  type    = string
  default = null
}

variable "policies_to_attach" {
  type    = map(string)
  default = {}
}
