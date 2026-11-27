# EventBridge rules: start the sync Step Function when the live Axiell adapter
# (axiell.adapter.completed) or the UAT one (axiell_uat.adapter.completed)
# completes.

data "aws_iam_policy_document" "eventbridge_assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["events.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "eventbridge_exec" {
  name               = "${var.namespace}-adapter-eventbridge"
  assume_role_policy = data.aws_iam_policy_document.eventbridge_assume_role.json
}

data "aws_iam_policy_document" "eventbridge_policy" {
  statement {
    effect    = "Allow"
    actions   = ["states:StartExecution"]
    resources = [aws_sfn_state_machine.state_machine.arn]
  }
}

resource "aws_iam_role_policy" "eventbridge_policy" {
  name   = "${var.namespace}-adapter-eventbridge-policy"
  role   = aws_iam_role.eventbridge_exec.id
  policy = data.aws_iam_policy_document.eventbridge_policy.json
}

resource "aws_cloudwatch_event_rule" "axiell_adapter_completed" {
  count = var.live_sync_enabled ? 1 : 0

  name           = "${var.namespace}-adapter-axiell-completed"
  event_bus_name = var.event_bus_name
  description    = "Trigger the Axiell to Folio sync when the Axiell adapter completes processing changesets"

  event_pattern = jsonencode({
    source      = ["axiell.adapter"]
    detail-type = ["axiell.adapter.completed"]
    detail = {
      transformer_type = ["axiell"]
    }
  })

  # Trigger-window schedules own this after apply; ignore state changes so an
  # out-of-hours apply cannot re-enable the trigger against a stopped sandbox.
  state = local.trigger_window_enabled ? "DISABLED" : "ENABLED"

  lifecycle {
    ignore_changes = [state]
  }
}

resource "aws_cloudwatch_event_target" "axiell_sync_step_function" {
  count = var.live_sync_enabled ? 1 : 0

  rule           = aws_cloudwatch_event_rule.axiell_adapter_completed[0].name
  event_bus_name = var.event_bus_name
  target_id      = "${var.namespace}-adapter-step-function"
  arn            = aws_sfn_state_machine.state_machine.arn
  role_arn       = aws_iam_role.eventbridge_exec.arn

  # EventBridge passes the full event to the Step Function, which extracts
  # $.detail.changeset_ids, $.detail.job_id and $.detail.transformer_type.

  depends_on = [
    aws_iam_role_policy.eventbridge_policy
  ]
}

# The UAT adapter has a rule of its own because its runs go to the FOLIO test
# environment, which is always up, so the trigger window that confines the live
# adapter's runs to the sandbox's hours does not apply.
# transformer_type tells the Lambda to read the UAT tables, and the Lambda holds
# a UAT run to the test environment whatever the event or FOLIO_TARGET say.
resource "aws_cloudwatch_event_rule" "axiell_uat_adapter_completed" {
  name           = "${var.namespace}-adapter-axiell-uat-completed"
  event_bus_name = var.event_bus_name
  description    = "Trigger the Axiell to Folio sync when the Axiell UAT adapter completes processing changesets"

  event_pattern = jsonencode({
    source      = ["axiell_uat.adapter"]
    detail-type = ["axiell_uat.adapter.completed"]
    detail = {
      transformer_type = ["axiell_uat"]
    }
  })
}

resource "aws_cloudwatch_event_target" "axiell_uat_sync_step_function" {
  rule           = aws_cloudwatch_event_rule.axiell_uat_adapter_completed.name
  event_bus_name = var.event_bus_name
  target_id      = "${var.namespace}-adapter-uat-step-function"
  arn            = aws_sfn_state_machine.state_machine.arn
  role_arn       = aws_iam_role.eventbridge_exec.arn

  depends_on = [
    aws_iam_role_policy.eventbridge_policy
  ]
}

# The live rule and target gained a count (live_sync_enabled).
moved {
  from = aws_cloudwatch_event_rule.axiell_adapter_completed
  to   = aws_cloudwatch_event_rule.axiell_adapter_completed[0]
}

moved {
  from = aws_cloudwatch_event_target.axiell_sync_step_function
  to   = aws_cloudwatch_event_target.axiell_sync_step_function[0]
}
