resource "aws_cloudwatch_metric_alarm" "incremental_pipeline_run_aborted_alarm" {
  alarm_name          = "concepts_daily_run_aborted_alarm"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ExecutionsAborted"
  namespace           = "AWS/States"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  actions_enabled     = true
  alarm_description   = "ExecutionsAborted detected"
  dimensions = {
    StateMachineArn = module.catalogue_graph_pipeline_incremental_state_machine.state_machine_arn
  }
  alarm_actions = [data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn]
}

resource "aws_cloudwatch_metric_alarm" "concepts_daily_run_failed_alarm" {
  alarm_name          = "concepts_daily_run_failed_alarm"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ExecutionsFailed"
  namespace           = "AWS/States"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  actions_enabled     = true
  alarm_description   = "ExecutionsFailed detected"
  dimensions = {
    StateMachineArn = module.catalogue_graph_pipeline_incremental_state_machine.state_machine_arn
  }
  alarm_actions = [data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn]
}

resource "aws_cloudwatch_metric_alarm" "concepts_daily_run_timedout_alarm" {
  alarm_name          = "concepts_daily_run_timedout_alarm"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ExecutionsTimedOut"
  namespace           = "AWS/States"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  actions_enabled     = true
  alarm_description   = "ExecutionsTimedOut detected"
  dimensions = {
    StateMachineArn = module.catalogue_graph_pipeline_incremental_state_machine.state_machine_arn
  }
  alarm_actions = [data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn]
}

resource "aws_cloudwatch_metric_alarm" "concepts_monthly_run_aborted_alarm" {
  alarm_name          = "concepts_monthly_run_aborted_alarm"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ExecutionsAborted"
  namespace           = "AWS/States"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  actions_enabled     = true
  alarm_description   = "ExecutionsAborted detected"
  dimensions = {
    StateMachineArn = module.catalogue_graph_pipeline_monthly_state_machine.state_machine_arn
  }
  alarm_actions = [data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn]
}

resource "aws_cloudwatch_metric_alarm" "concepts_monthly_run_failed_alarm" {
  alarm_name          = "concepts_monthly_run_failed_alarm"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ExecutionsFailed"
  namespace           = "AWS/States"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  actions_enabled     = true
  alarm_description   = "ExecutionsFailed detected"
  dimensions = {
    StateMachineArn = module.catalogue_graph_pipeline_monthly_state_machine.state_machine_arn
  }
  alarm_actions = [data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn]
}

resource "aws_cloudwatch_metric_alarm" "concepts_monthly_run_timedout_alarm" {
  alarm_name          = "concepts_monthly_run_timedout_alarm"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ExecutionsTimedOut"
  namespace           = "AWS/States"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  actions_enabled     = true
  alarm_description   = "ExecutionsTimedOut detected"
  dimensions = {
    StateMachineArn = module.catalogue_graph_pipeline_monthly_state_machine.state_machine_arn
  }
  alarm_actions = [data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn]
}
