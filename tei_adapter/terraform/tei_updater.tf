module "tei_updater_lambda" {
  source = "./tei_updater"

  trigger_interval_minutes = 30
  github_url               = "https://api.github.com/repos/wellcomecollection/wellcome-collection-tei/git/trees/main?recursive=true"

  lambda_error_alarm_arn = local.lambda_error_alarm_arn
  infra_bucket           = local.infra_bucket

  tei_tree_key = "tei_tree.json"

  namespace = local.namespace
}

