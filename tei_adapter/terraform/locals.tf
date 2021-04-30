locals {
  infra_bucket           = data.terraform_remote_state.shared_infra.outputs.infra_bucket
  aws_region             = "eu-west-1"
  lambda_error_alarm_arn = data.terraform_remote_state.shared_infra.outputs.lambda_error_alarm_arn
}
