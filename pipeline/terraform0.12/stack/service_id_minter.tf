module "id_minter_topic" {
  source = "../modules/topic"

  name       = "${local.namespace_hyphen}_id_minter"
//  role_names = ["${module.id_minter.task_role_name}"]
  role_names = []
  messages_bucket_arn = aws_s3_bucket.messages.arn
}

//module "id_minter_queue" {
//  source      = "../modules/queue"
//  name  = "${local.namespace_hyphen}_id_minter"
////  topic_names = ["${module.merger_topic.name}"]
//  topic_names = []
//  topic_count = 1
//  aws_region      = var.aws_region
//  dlq_alarm_arn = var.dlq_alarm_arn
//  role_names = [module.id_minter.task_role_name]
//  topic_arns = ["module.id_minter_topic.name"]
//}
//
//module "id_minter" {
//  source = "../modules/service/pipeline_service"
//
//  service_name = "${local.namespace_hyphen}_id_minter"
//
//  container_image = "${local.id_minter_image}"
//
//  security_group_ids = [
//    "${module.egress_security_group.sg_id}",
//    "${var.rds_ids_access_security_group_id}",
//    "${aws_security_group.interservice.id}",
//  ]
//
//  cluster_name  = "${aws_ecs_cluster.cluster.name}"
//  cluster_arn    = "${aws_ecs_cluster.cluster.id}"
//  namespace_id  = "${aws_service_discovery_private_dns_namespace.namespace.id}"
//  subnets       = "${var.subnets}"
//  aws_region    = "${var.aws_region}"
//  logstash_host = "${local.logstash_host}"
//
//  env_vars = {
//    metrics_namespace    = "${local.namespace_hyphen}_id_minter"
//    messages_bucket_name = "${aws_s3_bucket.messages.id}"
//
//    queue_url       = "${module.id_minter_queue.id}"
//    topic_arn       = "${module.id_minter_topic.arn}"
//    max_connections = 8
//  }
//
//  env_vars_length = 5
//
//  secret_env_vars = {
//    cluster_url = "catalogue/id_minter/rds_host"
//    db_port     = "catalogue/id_minter/rds_port"
//    db_username = "catalogue/id_minter/rds_user"
//    db_password = "catalogue/id_minter/rds_password"
//  }
//
//  secret_env_vars_length = "4"
//
//  // The maximum number of connections to RDS is 45.
//  // Each id minter task is configured to have 8 connections
//  // in the connection pool (see `max_connections` parameterabove).
//  // To avoid exceeding the maximum nuber of connections to RDS,
//  // the maximum capacity for the id minter should be no higher than 5
//  max_capacity = 5
//
//  messages_bucket_arn = "${aws_s3_bucket.messages.arn}"
//  queue_read_policy   = "${module.id_minter_queue.read_policy}"
//}