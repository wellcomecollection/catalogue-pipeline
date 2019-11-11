locals {
  # Release URIs

  api_release_uri                = "${data.aws_ssm_parameter.api_release_uri.value}"
  api_nginx_release_uri          = "${data.aws_ssm_parameter.api_nginx_release_uri.value}"
  snapshot_generator_release_uri = "${data.aws_ssm_parameter.snapshot_generator_release_uri.value}"
  update_api_docs_release_uri    = "${data.aws_ssm_parameter.update_api_docs_release_uri.value}"

  # API pins

  production_api     = "remus"
  pinned_nginx       = "760097843905.dkr.ecr.eu-west-1.amazonaws.com/uk.ac.wellcome/nginx_api-gw:bad0dbfa548874938d16496e313b05adb71268b7"
  pinned_remus_api   = "760097843905.dkr.ecr.eu-west-1.amazonaws.com/uk.ac.wellcome/api:785c0e98827b3ed174403b936e966e3b341ed085"
  pinned_romulus_api = "760097843905.dkr.ecr.eu-west-1.amazonaws.com/uk.ac.wellcome/api:d49dbf416ff5c8de80b795425c15eca8c7646b8c"
  romulus_es_config = {
    index_v2 = "v2-20191007"
    doc_type = "work"
  }
  remus_es_config = {
    index_v2 = "v2-20191007"
    doc_type = "work"
  }

  # Blue / Green config

  romulus_is_prod     = "${local.production_api == "romulus" ? "true" : "false"}"
  remus_is_prod       = "${local.production_api == "remus" ? "true" : "false"}"
  romulus_app_uri     = "${local.pinned_romulus_api != "" ? local.pinned_romulus_api : local.api_release_uri}"
  remus_app_uri       = "${local.pinned_remus_api != "" ? local.pinned_remus_api : local.api_release_uri}"
  stage_api           = "${local.remus_is_prod == "false" ? "remus" : "romulus"}"
  remus_task_number   = "${local.remus_is_prod == "true" ? 3 : 1}"
  romulus_task_number = "${local.romulus_is_prod == "true" ? 3 : 1}"

  # Catalogue API

  vpc_id                         = "${data.terraform_remote_state.shared_infra.catalogue_vpc_delta_id}"
  private_subnets                = "${data.terraform_remote_state.shared_infra.catalogue_vpc_delta_private_subnets}"
  namespace                      = "catalogue-api"
  nginx_container_uri            = "${local.pinned_nginx}"
  gateway_server_error_alarm_arn = "${data.terraform_remote_state.shared_infra.gateway_server_error_alarm_arn}"

  # Data API

  prod_es_config = {
    index_v2 = "${local.romulus_is_prod == "true" ? local.romulus_es_config["index_v2"] : local.remus_es_config["index_v2"]}"
    doc_type = "${local.romulus_is_prod == "true" ? local.romulus_es_config["doc_type"] : local.remus_es_config["doc_type"]}"
  }
  release_id = "${local.romulus_is_prod == "true" ? local.pinned_romulus_api : local.pinned_remus_api}"
}
