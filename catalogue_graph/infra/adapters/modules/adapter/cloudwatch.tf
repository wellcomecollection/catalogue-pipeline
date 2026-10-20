module "adapter_state_machine_alarms" {
  source = "../../../../../pipeline/terraform/modules/state_machine_alarms"

  state_machine_arn = aws_sfn_state_machine.state_machine.arn
  alarm_name_prefix = "${var.namespace}-adapter"

  default_alarm_configuration = {
    alarm_actions = [data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn]
  }
}

# The enrichment step skips inventory records it can't store (a holdings record
# without an id, an item linked to no holdings) rather than failing the window,
# so each skip needs to reach someone who can get it fixed in FOLIO.
resource "aws_cloudwatch_metric_alarm" "enrichment_rejected_records" {
  count = var.enable_item_enrichment ? 1 : 0

  alarm_name          = "${var.namespace}-adapter-enrichment-rejected-records"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "rejected_record_count"
  namespace           = "catalogue_adapters"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  treat_missing_data  = "notBreaching"
  alarm_description   = "${var.namespace} enrichment left out inventory records that failed validation; check the step's report under reports/${var.namespace}/adapter_enrichment/ in the adapter bucket"

  dimensions = {
    adapter_type  = var.namespace
    pipeline_step = "adapter_enrichment"
  }

  alarm_actions = [data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn]
}
