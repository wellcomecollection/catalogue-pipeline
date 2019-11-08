locals {
  namespace    = "catalogue_api"
  prod_name    = "prod"
  staging_name = "staging"

  prod_es_config = {
    index_v2 = "v2-20191007"
    doc_type = "work"
  }

  staging_es_config = {
    index_v2 = "v2-20191007"
    doc_type = "work"
  }

  prod_task_number    = 3
  staging_task_number = 1

  prod_listener_port    = "80"
  staging_listener_port = "8080"

  vpc_id                         = "${data.terraform_remote_state.shared_infra.catalogue_vpc_id}"
  private_subnets                = "${data.terraform_remote_state.shared_infra.catalogue_vpc_private_subnets}"
  gateway_server_error_alarm_arn = "${data.terraform_remote_state.shared_infra.gateway_server_error_alarm_arn}"
}
