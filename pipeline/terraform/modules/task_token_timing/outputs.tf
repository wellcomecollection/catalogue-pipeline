# Timings for Step Functions states that launch an ECS task and wait for its task
# token. Shared because the two numbers are easy to copy and one of them does not
# travel: a value sized for Fargate will kill a healthy EC2 task during cold start.
#
# The timeout is an outer bound on the whole state. Liveness is the heartbeat's job:
# tasks beat every 60s (HEARTBEAT_INTERVAL_SECONDS in catalogue_graph/src/utils/steps.py),
# so the heartbeat only has to cover the gap before the first beat, which is however
# long the task takes to actually start running.

output "fargate_heartbeat_seconds" {
  # Measured cold start on catalogue-2026-07-03: 45 to 50s across six graph tasks.
  value       = 5 * 60
  description = "Heartbeat for a task that launches on Fargate."
}

output "ec2_capacity_provider_heartbeat_seconds" {
  # The inferrer ASG runs at min=0, so every task waits for an instance to launch
  # and, in a burst, for one of the 12 to free up. The worst single state during the
  # round 2 reindex was 769s across five bursts (609 to 769s), which bounds the wait
  # before a first beat. 20 minutes leaves room for a larger burst.
  value       = 20 * 60
  description = "Heartbeat for a task that launches on an EC2 capacity provider."
}
