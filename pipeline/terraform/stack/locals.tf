locals {
  logstash_transit_service_name = "${local.namespace_hyphen}_logstash_transit"
  logstash_host                 = "${local.logstash_transit_service_name}.${local.namespace_hyphen}"
  namespace_hyphen              = "${replace(var.namespace,"_","-")}"
}
