locals {
 namespace = "mets-adapter"
 storage_notifications_topic = ""
 logstash_transit_service_name = "${local.namespace}_logstash_transit"
 logstash_host                 = "${local.logstash_transit_service_name}.${local.namespace}"

 bag_api_url = "https://api.wellcomecollection.org/storage/v1/bags"
 oauth_url = "https://auth.wellcomecollection.org/oauth2/token"

 storage_bucket = "wellcomecollection-storage"

 # VHS
 vhs_mets_full_access_policy = "${data.terraform_remote_state.catalogue_infra_critical.vhs_mets_full_access_policy}"
 vhs_mets_adapter_table_name = "${data.terraform_remote_state.catalogue_infra_critical.vhs_mets_table_name}"
 vhs_mets_adapter_bucket_name = "${data.terraform_remote_state.catalogue_infra_critical.vhs_mets_bucket_name}"

 # Infra stuff
 infra_bucket = "${data.terraform_remote_state.shared_infra.infra_bucket}"
 account_id = "${data.aws_caller_identity.current.account_id}"
 aws_region = "eu-west-1"
 dlq_alarm_arn = "${data.terraform_remote_state.shared_infra.dlq_alarm_arn}"
 vpc_id = "${data.terraform_remote_state.shared_infra.catalogue_vpc_delta_id}"
 private_subnets = "${data.terraform_remote_state.shared_infra.catalogue_vpc_delta_private_subnets}"
}
