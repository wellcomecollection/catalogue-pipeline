module "folio_adapter_state_machine_alarms" {
  source = "../../../../pipeline/terraform/modules/state_machine_alarms"

  state_machine_arn = aws_sfn_state_machine.state_machine.arn
  alarm_name_prefix = "folio-adapter"

  default_alarm_configuration = {
    alarm_actions = [data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn]
  }
}
