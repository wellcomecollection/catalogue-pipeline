locals {
  lambda_error_alarm_arn      = "${data.terraform_remote_state.shared_infra.lambda_error_alarm_arn}"
  dlq_alarm_arn               = "${data.terraform_remote_state.shared_infra.dlq_alarm_arn}"
  service_discovery_namespace = "${aws_service_discovery_private_dns_namespace.namespace.id}"

  vpc_id          = "${data.terraform_remote_state.shared_infra.catalogue_vpc_id}"
  private_subnets = "${data.terraform_remote_state.shared_infra.catalogue_vpc_private_subnets}"

  infra_bucket = "wellcomecollection-catalogue-infra-delta"
}
