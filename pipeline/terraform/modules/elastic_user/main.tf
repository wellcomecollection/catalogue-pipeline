variable "pipeline_date" {
  type = string
}

variable "name" {
  type = string
}

variable "roles" {
  type = list(string)
}

resource "random_password" "password" {
  # Note: this is for back-compatibility with an earlier script, which
  # used `secrets.token_hex()` in Python.
  length = 64
}

resource "elasticstack_elasticsearch_security_user" "user" {
  username = var.name
  password = random_password.password.result
  roles    = var.roles
}

locals {
  secret_prefix = "elasticsearch/pipeline_storage_${var.pipeline_date}/${var.name}"
}

module "secrets" {
  source = "github.com/wellcomecollection/terraform-aws-secrets?ref=v1.0.0"

  key_value_map = {
    "${local.secret_prefix}/es_username" = var.name
    "${local.secret_prefix}/es_password" = random_password.password.result
  }
}
