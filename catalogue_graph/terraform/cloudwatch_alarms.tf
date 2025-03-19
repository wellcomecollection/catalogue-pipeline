resource "aws_cloudwatch_metric_alarm" "concepts_daily_run_aborted_alarm" {
  alarm_name          = "concepts_daily_run_aborted_alarm"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ExecutionsAborted"
  namespace           = "AWS/States"
  period              = 300
  statistic           = "Sum"
  threshold           = 1
  actions_enabled     = true
  alarm_description   = "Alarm when ExecutionsAborted exceeds 1"
  dimensions = {
    StateMachineArn = "arn:aws:states:eu-west-1:760097843905:stateMachine:concepts-pipeline_daily"
  }
  alarm_actions = [ data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn ]
}

resource "aws_cloudwatch_metric_alarm" "concepts_daily_run_failed_alarm" {
  alarm_name          = "concepts_daily_run_failed_alarm"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ExecutionsFailed"
  namespace           = "AWS/States"
  period              = 300
  statistic           = "Sum"
  threshold           = 1
  actions_enabled     = true
  alarm_description   = "Alarm when ExecutionsFailed exceeds 1"
  dimensions = {
    StateMachineArn = "arn:aws:states:eu-west-1:760097843905:stateMachine:concepts-pipeline_daily"
  }
  alarm_actions = [ data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn ]
}

resource "aws_cloudwatch_metric_alarm" "concepts_daily_run_timedout_alarm" {
  alarm_name          = "concepts_daily_run_timedout_alarm"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ExecutionsTimedOut"
  namespace           = "AWS/States"
  period              = 300
  statistic           = "Sum"
  threshold           = 1
  actions_enabled     = true
  alarm_description   = "Alarm when ExecutionsTimedOut exceeds 1"
  dimensions = {
    StateMachineArn = "arn:aws:states:eu-west-1:760097843905:stateMachine:concepts-pipeline_daily"
  }
  alarm_actions = [ data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn ]
}

resource "aws_cloudwatch_metric_alarm" "concepts_monthly_run_aborted_alarm" {
  alarm_name          = "concepts_monthly_run_aborted_alarm"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ExecutionsAborted"
  namespace           = "AWS/States"
  period              = 300
  statistic           = "Sum"
  threshold           = 1
  actions_enabled     = true
  alarm_description   = "Alarm when ExecutionsAborted exceeds 1"
  dimensions = {
    StateMachineArn = "arn:aws:states:eu-west-1:760097843905:stateMachine:concepts-pipeline_monthly"
  }
  alarm_actions = [ data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn ]
}

resource "aws_cloudwatch_metric_alarm" "concepts_monthly_run_failed_alarm" {
  alarm_name          = "concepts_monthly_run_failed_alarm"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ExecutionsFailed"
  namespace           = "AWS/States"
  period              = 300
  statistic           = "Sum"
  threshold           = 1
  actions_enabled     = true
  alarm_description   = "Alarm when ExecutionsFailed exceeds 1"
  dimensions = {
    StateMachineArn = "arn:aws:states:eu-west-1:760097843905:stateMachine:concepts-pipeline_monthly"
  }
  alarm_actions = [ data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn ]
}

resource "aws_cloudwatch_metric_alarm" "concepts_monthly_run_timedout_alarm" {
  alarm_name          = "concepts_monthly_run_timedout_alarm"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ExecutionsTimedOut"
  namespace           = "AWS/States"
  period              = 300
  statistic           = "Sum"
  threshold           = 1
  actions_enabled     = true
  alarm_description   = "Alarm when ExecutionsTimedOut exceeds 1"
  dimensions = {
    StateMachineArn = "arn:aws:states:eu-west-1:760097843905:stateMachine:concepts-pipeline_monthly"
  }
  alarm_actions = [ data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn ]
}