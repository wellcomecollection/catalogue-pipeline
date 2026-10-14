# Phase 1: apply with -target=module.project. The elasticstack.serverless
# provider reads the bootstrap key this creates, so nothing else can plan
# until it exists.
module "project" {
  source = "./modules/es_serverless_project"

  name      = "pipeline-storage-${local.pipeline_date}"
  region_id = local.region_id

  traffic_filter_ids = concat(
    var.attach_private_policy ? [ec_serverless_traffic_filter.private.id] : [],
    length(var.allowed_ip_cidrs) > 0 ? [ec_serverless_traffic_filter.ip[0].id] : [],
  )

  tags = {
    purpose = "platform-6714-spike"
  }
}
