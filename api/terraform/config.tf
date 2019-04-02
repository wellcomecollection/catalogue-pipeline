data "aws_ssm_parameter" "infra_bucket" {
  name = "/api/config/prod/infra_bucket"
}

locals {
  infra_bucket = "${data.aws_ssm_parameter.infra_bucket.value}"
}
