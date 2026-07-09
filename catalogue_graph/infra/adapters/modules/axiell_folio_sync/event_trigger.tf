# EventBridge rule: start the sync Step Function on axiell.adapter.completed events.

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
  name               = "${var.namespace}-eventbridge-exec"
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
  name   = "${var.namespace}-eventbridge-policy"
  role   = aws_iam_role.eventbridge_exec.id
  policy = data.aws_iam_policy_document.eventbridge_policy.json
}

resource "aws_cloudwatch_event_rule" "axiell_adapter_completed" {
  name           = "${var.namespace}-axiell-adapter-completed"
  event_bus_name = var.event_bus_name
  description    = "Trigger the Axiell → FOLIO sync when the adapter completes processing changesets"

  event_pattern = jsonencode({
    source      = ["axiell.adapter"]
    detail-type = ["axiell.adapter.completed"]
    detail = {
      transformer_type = ["axiell"]
    }
  })
}

resource "aws_cloudwatch_event_target" "axiell_sync_step_function" {
  rule           = aws_cloudwatch_event_rule.axiell_adapter_completed.name
  event_bus_name = var.event_bus_name
  target_id      = "${var.namespace}-step-function"
  arn            = aws_sfn_state_machine.state_machine.arn
  role_arn       = aws_iam_role.eventbridge_exec.arn

  # EventBridge passes the full event to the Step Function, which extracts
  # $.detail.changeset_ids, $.detail.job_id and $.detail.transformer_type.

  depends_on = [
    aws_iam_role_policy.eventbridge_policy
  ]
}
