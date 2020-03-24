resource "aws_cloudwatch_metric_alarm" "5xx_alarm" {
  alarm_name          = "catalogue-api-${local.prod_name}-5xx-alarm"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  metric_name         = "5XXError"
  namespace           = "AWS/ApiGateway"
  period              = "60"
  statistic           = "Sum"
  threshold           = "0"

  dimensions {
    ApiName = "${local.api_gateway_name}"
    Stage   = "${local.prod_name}"
  }

  alarm_actions = ["${local.gateway_server_error_alarm_arn}"]
}
