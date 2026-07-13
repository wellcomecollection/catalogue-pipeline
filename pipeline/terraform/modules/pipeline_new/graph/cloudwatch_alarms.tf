module "graph_pipeline_incremental_state_machine_alarms" {
  source = "../../state_machine_alarms"

  state_machine_arn = module.catalogue_graph_pipeline_incremental_state_machine.state_machine_arn
  alarm_name_prefix = "graph-pipeline-incremental-run"
  alarm_name_suffix = "-${var.pipeline_date}"

  default_alarm_configuration = {
    alarm_actions = [data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn]
  }
}

module "graph_pipeline_monthly_state_machine_alarms" {
  source = "../../state_machine_alarms"

  state_machine_arn = module.catalogue_graph_pipeline_monthly_state_machine.state_machine_arn
  alarm_name_prefix = "graph-pipeline-monthly-run"
  alarm_name_suffix = "-${var.pipeline_date}"

  default_alarm_configuration = {
    alarm_actions = [data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn]
  }
}
