data "aws_s3_bucket" "public_data" {
  bucket = "wellcomecollection-data-public-delta"
}

locals {
  cluster_arn  = data.terraform_remote_state.data_api.outputs.cluster_arn
  cluster_name = data.terraform_remote_state.data_api.outputs.cluster_name

  public_object_key_v2    = "catalogue/v2/works.json.gz"
  public_data_bucket_name = data.aws_s3_bucket.public_data.id

  snapshot_generator_image = data.terraform_remote_state.api_shared.outputs.ecr_snapshot_generator_repository_url

  lambda_error_alarm_arn = data.terraform_remote_state.shared.outputs.lambda_error_alarm_arn
  dlq_alarm_arn          = data.terraform_remote_state.shared.outputs.dlq_alarm_arn

  vpc_id  = data.terraform_remote_state.catalogue_account.outputs.catalogue_vpc_id
  subnets = data.terraform_remote_state.catalogue_account.outputs.catalogue_vpc_private_subnets
}
