locals {
  default_alarm_settings = {
    alarm_actions   = try(var.default_alarm_configuration["alarm_actions"], [])
    period          = try(var.default_alarm_configuration["period"], 300)
    actions_enabled = try(var.default_alarm_configuration["actions_enabled"], true)
  }

  alarm_definitions = {
    aborted = {
      alarm_suffix      = "aborted"
      metric_name       = "ExecutionsAborted"
      alarm_description = "ExecutionsAborted detected"
    }
    failed = {
      alarm_suffix      = "failed"
      metric_name       = "ExecutionsFailed"
      alarm_description = "ExecutionsFailed detected"
    }
    timed_out = {
      alarm_suffix      = "timeout"
      metric_name       = "ExecutionsTimedOut"
      alarm_description = "ExecutionsTimedOut detected"
    }
  }

  alarm_configurations = {
    for alarm_key, alarm_definition in local.alarm_definitions :
    alarm_key => {
      alarm_suffix      = alarm_definition.alarm_suffix
      metric_name       = alarm_definition.metric_name
      alarm_description = try(var.alarm_overrides[alarm_key]["alarm_description"], alarm_definition.alarm_description)
      alarm_actions     = try(var.alarm_overrides[alarm_key]["alarm_actions"], local.default_alarm_settings.alarm_actions)
      period            = try(var.alarm_overrides[alarm_key]["period"], local.default_alarm_settings.period)
      actions_enabled   = try(var.alarm_overrides[alarm_key]["actions_enabled"], local.default_alarm_settings.actions_enabled)
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "state_machine" {
  for_each = local.alarm_configurations

  alarm_name          = format("%s-%s%s", var.alarm_name_prefix, each.value.alarm_suffix, var.alarm_name_suffix)
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = each.value.metric_name
  namespace           = "AWS/States"
  period              = each.value.period
  statistic           = "Sum"
  threshold           = 0
  actions_enabled     = each.value.actions_enabled
  alarm_description   = each.value.alarm_description

  dimensions = {
    StateMachineArn = var.state_machine_arn
  }

  alarm_actions = each.value.alarm_actions
}
