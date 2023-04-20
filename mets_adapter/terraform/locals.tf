locals {
  mets_adapter_image = "${aws_ecr_repository.mets_adapter_services["mets_adapter"].repository_url}:env.prod"

  namespace                       = "mets-adapter"
  storage_notifications_topic_arn = data.terraform_remote_state.storage_service.outputs.registered_bag_notifications_topic_arn

  bag_api_url = "https://api.wellcomecollection.org/storage/v1/bags"
  oauth_url   = "https://auth.wellcomecollection.org/oauth2/token"

  # Store
  mets_full_access_policy = data.aws_iam_policy_document.mets_dynamo_full_access_policy.json
  mets_adapter_table_name = aws_dynamodb_table.mets_adapter_table.id

  # Infra stuff
  dlq_alarm_arn   = data.terraform_remote_state.monitoring.outputs.platform_dlq_alarm_topic_arn
  vpc_id          = local.catalogue_vpcs["catalogue_vpc_delta_id"]
  private_subnets = local.catalogue_vpcs["catalogue_vpc_delta_private_subnets"]
}
