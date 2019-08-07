locals {
  logstash_transit_service_name = "${var.namespace}_logstash_transit"
  logstash_host                 = "${local.logstash_transit_service_name}.${var.namespace}"
}
