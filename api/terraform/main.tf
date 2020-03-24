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
  cluster_name = "${local.cluster_name}"
  api_id       = "${local.api_gateway_id}"

  lb_arn           = "${local.nlb_arn}"
  lb_ingress_sg_id = "${local.service_lb_ingress_security_group_id}"

  logstash_host = "${local.logstash_host}"

  interservice_sg_id = "${local.interservice_security_group_id}"
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
  cluster_name = "${local.cluster_name}"
  api_id       = "${local.api_gateway_id}"

  lb_arn           = "${local.nlb_arn}"
  lb_ingress_sg_id = "${local.service_lb_ingress_security_group_id}"

  logstash_host = "${local.logstash_host}"

  interservice_sg_id = "${local.interservice_security_group_id}"
}
