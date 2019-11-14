locals {
  logstash_transit_service_name = "${local.namespace_underscores}_logstash_transit"
  logstash_host                 = "${local.logstash_transit_service_name}.${local.namespace_underscores}"
  namespace_underscores = "${replace(var.namespace,"-","_")}"
  namespace_hyphen = "${replace(var.namespace,"_","-")}"
}
