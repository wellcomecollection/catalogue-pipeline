locals {
  infra_bucket           = data.terraform_remote_state.shared_infra.outputs.infra_bucket
  window_generator_interval = "30 minutes"
  aws_region             = "eu-west-1"
}
