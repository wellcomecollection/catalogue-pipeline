# Confines the sync trigger to working hours.
#
# Only created when the run window is enabled, which in practice means while the
# scheduled pipeline is pointed at the FOLIO dev sandbox. The sandbox is stopped
# out of hours by its own schedule in wellcomecollection/folio-dev-server, and
# the Axiell adapter publishes every 15 minutes, so without this the sync would
# spend each night failing to connect and retrying.
#
# The rule is left enabled at apply time; these schedules only flip it after the
# next boundary. Applying outside the window therefore leaves it on until the
# evening disable fires.

locals {
  trigger_window_enabled = var.trigger_window != null
}

data "aws_iam_policy_document" "scheduler_assume_role" {
  count = local.trigger_window_enabled ? 1 : 0

  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "scheduler" {
  count = local.trigger_window_enabled ? 1 : 0

  name               = "${var.namespace}-adapter-trigger-window"
  assume_role_policy = data.aws_iam_policy_document.scheduler_assume_role[0].json
}

data "aws_iam_policy_document" "scheduler_toggle_rule" {
  count = local.trigger_window_enabled ? 1 : 0

  statement {
    effect    = "Allow"
    actions   = ["events:EnableRule", "events:DisableRule"]
    resources = [aws_cloudwatch_event_rule.axiell_adapter_completed.arn]
  }
}

resource "aws_iam_role_policy" "scheduler_toggle_rule" {
  count = local.trigger_window_enabled ? 1 : 0

  name   = "${var.namespace}-adapter-trigger-window-policy"
  role   = aws_iam_role.scheduler[0].id
  policy = data.aws_iam_policy_document.scheduler_toggle_rule[0].json
}

# enableRule and disableRule take the rule name and bus, not an ARN.
locals {
  trigger_rule_input = local.trigger_window_enabled ? jsonencode({
    Name         = aws_cloudwatch_event_rule.axiell_adapter_completed.name
    EventBusName = var.event_bus_name
  }) : null
}

resource "aws_scheduler_schedule" "trigger_on" {
  count = local.trigger_window_enabled ? 1 : 0

  name = "${var.namespace}-adapter-trigger-on"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression          = var.trigger_window.start_expression
  schedule_expression_timezone = var.trigger_window.timezone

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:eventbridge:enableRule"
    role_arn = aws_iam_role.scheduler[0].arn
    input    = local.trigger_rule_input
  }
}

resource "aws_scheduler_schedule" "trigger_off" {
  count = local.trigger_window_enabled ? 1 : 0

  name = "${var.namespace}-adapter-trigger-off"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression          = var.trigger_window.stop_expression
  schedule_expression_timezone = var.trigger_window.timezone

  target {
    arn      = "arn:aws:scheduler:::aws-sdk:eventbridge:disableRule"
    role_arn = aws_iam_role.scheduler[0].arn
    input    = local.trigger_rule_input
  }
}
