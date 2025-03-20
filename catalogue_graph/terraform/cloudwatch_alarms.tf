resource "aws_cloudwatch_metric_alarm" "concepts_daily_run_aborted_alarm" {
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
  threshold           = 0
  actions_enabled     = true
  alarm_description   = "ExecutionsFailed detected"
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
  threshold           = 0
  actions_enabled     = true
  alarm_description   = "ExecutionsTimedOut detected"
  dimensions = {
    StateMachineArn = "arn:aws:states:eu-west-1:760097843905:stateMachine:concepts-pipeline_daily"
  }
  alarm_actions = [ data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn ]
}

resource "aws_cloudwatch_metric_alarm" "concepts_daily_run_no_success_alarm" {
  alarm_name          = "concepts_daily_run_no_success_seen_alarm"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1176
  metric_name         = "ExecutionsSucceeded"
  namespace           = "AWS/States"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  actions_enabled     = true
  # this accounts for the fact that the pipeline doesn't run FRI-SAT-SUN
  alarm_description   = "No successful run of the daily concept pipeline for 98 hours."
  dimensions = {
    StateMachineArn = "arn:aws:states:eu-west-1:760097843905:stateMachine:concepts-pipeline_daily"
  }
  treat_missing_data  = "breaching" 
  alarm_actions       = [ data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn ]
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
  threshold           = 0
  actions_enabled     = true
  alarm_description   = "ExecutionsFailed detected"
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
  threshold           = 0
  actions_enabled     = true
  alarm_description   = "ExecutionsTimedOut detected"
  dimensions = {
    StateMachineArn = "arn:aws:states:eu-west-1:760097843905:stateMachine:concepts-pipeline_monthly"
  }
  alarm_actions = [ data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn ]
}