resource "aws_cloudwatch_metric_alarm" "alarm_5xx" {
  alarm_name          = "catalogue-api-prod-5xx-alarm"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "5XXError"
  namespace           = "AWS/ApiGateway"
  period              = "60"
  statistic           = "Sum"
  threshold           = "0"

  dimensions = {
    ApiName = local.api_gateway_name
    Stage   = "prod"
  }

  alarm_actions = [local.gateway_server_error_alarm_arn]
}
