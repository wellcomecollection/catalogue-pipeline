moved {
  from = module.pipeline.module.elastic
  to   = module.elastic
}

# Test services now use count
moved {
  from = module.pipeline.module.matcher_test
  to   = module.pipeline.module.matcher_test[0]
}

moved {
  from = module.pipeline.module.merger_test
  to   = module.pipeline.module.merger_test[0]
}

moved {
  from = module.pipeline.module.id_minter_test
  to   = module.pipeline.module.id_minter_test[0]
}

moved {
  from = module.pipeline.module.id_minter_test_state_machine
  to   = module.pipeline.module.id_minter_test_state_machine[0]
}

moved {
  from = module.pipeline.module.id_minter_test_state_machine_alarms
  to   = module.pipeline.module.id_minter_test_state_machine_alarms[0]
}

moved {
  from = module.pipeline.aws_scheduler_schedule.id_minter_test_schedule
  to   = module.pipeline.aws_scheduler_schedule.id_minter_test_schedule[0]
}

moved {
  from = module.pipeline.aws_iam_role.run_id_minter_test_role
  to   = module.pipeline.aws_iam_role.run_id_minter_test_role[0]
}

moved {
  from = module.pipeline.aws_iam_role_policy.run_id_minter_test_policy
  to   = module.pipeline.aws_iam_role_policy.run_id_minter_test_policy[0]
}
