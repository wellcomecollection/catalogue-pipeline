resource "aws_cloudwatch_metric_alarm" "ebsco_adapter_no_success_metric" {
  alarm_name          = "ebsco-adapter-no-success-metric"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ProcessDurationSuccess"
  namespace           = "ebsco_adapter"
  period              = 86400
  statistic           = "Sum"
  threshold           = 1
  alarm_description   = "No success metrics have been sent in the last 24 hours"
  alarm_actions       = [module.cloudwatch_alarm_to_slack_topic.arn]
  dimensions = {
    process_name = "scheduled"
  }
}
