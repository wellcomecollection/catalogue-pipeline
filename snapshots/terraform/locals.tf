data "aws_ecs_cluster" "data_api" {
  cluster_name = "data-api"
}

data "aws_ecr_repository" "snapshot_generator" {
  name = "uk.ac.wellcome/snapshot_generator"

  provider = aws.platform
}

data "aws_s3_bucket" "public_data" {
  bucket = "wellcomecollection-data-public-delta"
}

data "aws_s3_bucket" "infra" {
  bucket = "wellcomecollection-catalogue-infra-delta"
}

locals {
  cluster_arn  = data.aws_ecs_cluster.data_api.arn
  cluster_name = data.aws_ecs_cluster.data_api.cluster_name

  public_object_key_v2    = "catalogue/v2/works.json.gz"
  public_data_bucket_name = data.aws_s3_bucket.public_data.id

  infra_bucket = data.aws_s3_bucket.infra.id

  snapshot_generator_image = data.aws_ecr_repository.snapshot_generator.repository_url

  lambda_error_alarm_arn = data.terraform_remote_state.shared.outputs.lambda_error_alarm_arn
  dlq_alarm_arn          = data.terraform_remote_state.shared.outputs.dlq_alarm_arn

  vpc_id  = data.terraform_remote_state.catalogue_account.outputs.catalogue_vpc_id
  subnets = data.terraform_remote_state.catalogue_account.outputs.catalogue_vpc_private_subnets
}
