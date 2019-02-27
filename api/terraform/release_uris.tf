data "aws_ssm_parameter" "api_release_uri" {
  name = "/catalogue_api/images/latest/api"
}

data "aws_ssm_parameter" "api_nginx_release_uri" {
  name = "/catalogue_api/images/latest/nginx_api-gw"
}

data "aws_ssm_parameter" "snapshot_generator_release_uri" {
  name = "/catalogue_api/images/latest/snapshot_generator"
}

data "aws_ssm_parameter" "update_api_docs_release_uri" {
  name = "/catalogue_api/images/latest/update_api_docs"
}
