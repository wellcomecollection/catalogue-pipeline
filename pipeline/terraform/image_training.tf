# Inferrer model data persists across pipeline versions
# and is critical for inference in the pipeline to be possible

resource "aws_s3_bucket" "inferrer_model_core_data" {
  bucket = "wellcomecollection-inferrer-model-core-data"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_acl" "inferrer_model_core_data" {
  bucket = aws_s3_bucket.inferrer_model_core_data.id
  acl    = "private"
}

data "aws_ssm_parameter" "inferrer_lsh_model_key" {
  name = "/catalogue_pipeline/config/models/stage/lsh_model"
}

locals {
  inferrer_config = {
    model_bucket = aws_s3_bucket.inferrer_model_core_data.id
    model_key    = data.aws_ssm_parameter.inferrer_lsh_model_key.value
  }
}

# This defines the task definition for training the image inferrer model
# on the contents of a pipeline.
#
# We don't use it in a lot of pipelines, so we don't create it for every
# pipeline -- but we save the definitions so we can create it when needed.

module "image_training_2022-07-26" {
  source = "./modules/image_training"

  count = 0

  namespace       = "catalogue-2022-07-26"
  es_images_index = "images-indexed-2022-07-26"
  release_label   = "2022-07-26"

  inferrer_model_data_bucket_name = aws_s3_bucket.inferrer_model_core_data.id
}
