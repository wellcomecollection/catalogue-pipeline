variable "name" {}

variable "role_names" {
  type = list(string)
}

variable "subscriber_accounts"{
  type = list(string)
  default = []
}
