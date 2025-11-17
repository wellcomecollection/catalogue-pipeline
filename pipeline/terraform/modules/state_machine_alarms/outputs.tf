output "alarm_arns" {
  description = "Map of alarm ARNs keyed by alarm type (aborted, failed, timed_out)."
  value       = { for alarm_key, alarm in aws_cloudwatch_metric_alarm.state_machine : alarm_key => alarm.arn }
}

output "alarm_names" {
  description = "Map of alarm names keyed by alarm type (aborted, failed, timed_out)."
  value       = { for alarm_key, alarm in aws_cloudwatch_metric_alarm.state_machine : alarm_key => alarm.alarm_name }
}
