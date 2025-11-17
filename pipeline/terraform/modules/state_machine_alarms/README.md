# State Machine Alarms

This module creates a set of CloudWatch metric alarms for a Step Functions state machine. It provisions
alarms for aborted, failed, and timed-out executions and supports shared defaults alongside per-alarm overrides.

## Inputs

| Name | Description | Type | Default |
|------|-------------|------|---------|
| `state_machine_arn` | ARN of the Step Functions state machine to monitor. | `string` | n/a |
| `alarm_name_prefix` | Prefix used when constructing alarm names. | `string` | n/a |
| `alarm_name_suffix` | Suffix appended to each alarm name. | `string` | `""` |
| `default_alarm_configuration` | Map containing default values shared by all alarms. Supports `alarm_actions`, `period`, and `actions_enabled`. | `map(any)` | `{}` |
| `alarm_overrides` | Map keyed by alarm type (`aborted`, `failed`, `timed_out`) providing overrides for the same keys accepted by `default_alarm_configuration`, plus `alarm_description`. | `map(any)` | `{}` |

Default alarm settings fall back to:

- `alarm_actions = []`
- `period = 300`
- `actions_enabled = true`

## Outputs

| Name | Description |
|------|-------------|
| `alarm_arns` | Map of alarm ARNs keyed by alarm type. |
| `alarm_names` | Map of alarm names keyed by alarm type. |

## Usage

```hcl
module "example_state_machine_alarms" {
  source = "../../state_machine_alarms"

  state_machine_arn = aws_sfn_state_machine.example.arn
  alarm_name_prefix = "example-pipeline"
  alarm_name_suffix = "-${var.pipeline_date}"

  default_alarm_configuration = {
    alarm_actions   = [aws_sns_topic.pipeline_alerts.arn]
    period          = 120
    actions_enabled = true
  }

  alarm_overrides = {
    timed_out = {
      actions_enabled = false
    }
  }
}
```

In the example above, all alarms share the same SNS action, period, and enabled flag, while the timed-out alarm
has actions disabled entirely.
