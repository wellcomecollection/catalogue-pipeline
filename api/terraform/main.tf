module "catalogue_api_prod" {
  source = "./catalogue_api"

  environment           = "${local.prod_name}"
  api_container_image   = "${module.prod_images.services["api"]}"
  nginx_container_image = "${module.prod_images.services["nginx_api-gw"]}"
  listener_port         = "${local.prod_listener_port}"
  task_desired_count    = "${local.prod_task_number}"

  namespace    = "${local.namespace}"
  vpc_id       = "${local.vpc_id}"
  subnets      = ["${local.private_subnets}"]
  cluster_name = "${aws_ecs_cluster.cluster.name}"
  api_id       = "${aws_api_gateway_rest_api.api.id}"

  gateway_depends = [
    "${module.root_resource_integration.uri}",
    "${module.simple_integration.uri}",
  ]

  lb_arn           = "${module.nlb.arn}"
  lb_ingress_sg_id = "${aws_security_group.service_lb_ingress_security_group.id}"

  logstash_host = "${local.logstash_host}"

  interservice_sg_id = "${aws_security_group.interservice.id}"
}

module "catalogue_api_staging" {
  source = "./catalogue_api"

  environment           = "${local.staging_name}"
  api_container_image   = "${module.staging_images.services["api"]}"
  nginx_container_image = "${module.staging_images.services["nginx_api-gw"]}"
  listener_port         = "${local.staging_listener_port}"
  task_desired_count    = "${local.staging_task_number}"

  namespace    = "${local.namespace}"
  vpc_id       = "${local.vpc_id}"
  subnets      = ["${local.private_subnets}"]
  cluster_name = "${aws_ecs_cluster.cluster.name}"
  api_id       = "${aws_api_gateway_rest_api.api.id}"

  gateway_depends = [
    "${module.root_resource_integration.uri}",
    "${module.simple_integration.uri}",
  ]

  lb_arn           = "${module.nlb.arn}"
  lb_ingress_sg_id = "${aws_security_group.service_lb_ingress_security_group.id}"

  logstash_host = "${local.logstash_host}"

  interservice_sg_id = "${aws_security_group.interservice.id}"
}

module "data_api" {
  source = "./data_api"

  aws_region   = "${var.aws_region}"
  infra_bucket = "${local.infra_bucket}"

  snapshot_generator_release_uri = "${module.latest_images.services["snapshot_generator"]}"

  critical_slack_webhook = ""

  vpc_id          = "${local.vpc_id}"
  private_subnets = ["${local.private_subnets}"]
  route_zone_id   = "${local.routemaster_router53_zone_id}"

  providers = {
    aws.us_e1            = "aws.us_e1"
    aws.routemaster      = "aws.routemaster"
    aws.platform_account = "aws.platform_account"
  }
}
