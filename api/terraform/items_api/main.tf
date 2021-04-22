module "items_api_stage" {
  source = "../modules/service"

  service_name            = "items_api-stage"
  deployment_service_name = "items_api"
  deployment_service_env  = "stage"

  vpc_id            = local.vpc_id
  subnets           = local.private_subnets
  cluster_arn       = local.cluster_arn
  load_balancer_arn = local.nlb_arn

  container_port              = 9000
  load_balancer_listener_port = 6001

  container_image = local.api_container_image["stage"]

  desired_task_count = 1

  security_group_ids = [
    local.service_lb_ingress_security_group_id,
    local.egress_security_group_id,
    local.interservice_security_group_id,
    local.elastic_cloud_vpce_sg_id
  ]

  environment = {
    app_base_url       = "https://api.wellcomecollection.org/stacks/v1/items"
    context_url        = "https://api.wellcomecollection.org/stacks/v1/context.json"
    catalogue_base_url = "https://api.wellcomecollection.org/catalogue/v2"
    sierra_base_url    = "https://libsys.wellcomelibrary.org/iii/sierra-api"

    log_level = "DEBUG"

    metrics_namespace = "items_api"
  }

  secrets = {
    sierra_api_key    = "stacks/prod/sierra_api_key"
    sierra_api_secret = "stacks/prod/sierra_api_secret"
  }
}
