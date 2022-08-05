# This defines the task definition for training the image inferrer model
# on the contents of a pipeline.
#
# We don't use it in a lot of pipelines, so we don't create it for every
# pipeline -- but we save the definitions so we can create it when needed.

module "image_training_2022-07-26" {
  source = "./modules/image_training"

  namespace       = "catalogue-2022-07-26"
  es_images_index = "images-indexed-2022-07-26"
  release_label   = "2022-07-26"

  inferrer_model_data_bucket_name = aws_s3_bucket.inferrer_model_core_data.id
}

module "image_training_2022-08-04" {
  source = "./modules/image_training"

  namespace       = "catalogue-2022-08-04"
  es_images_index = "images-indexed-2022-08-04"
  release_label   = "2022-08-04"

  inferrer_model_data_bucket_name = aws_s3_bucket.inferrer_model_core_data.id
}