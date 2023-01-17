locals {
  ingestor_images_flush_interval_seconds = 30
}

module "ingestor_images_output_topic" {
  source = "../modules/topic"

  name       = "${local.namespace}_ingestor_images_output"
  role_names = [module.ingestor_images.task_role_name]
}

module "ingestor_images" {
  source = "../modules/fargate_service"

  name            = "ingestor_images"
  container_image = local.ingestor_images_image

  topic_arns = [
    module.image_inferrer_output_topic.arn,
  ]

  queue_visibility_timeout_seconds = local.ingestor_images_flush_interval_seconds + 60

  env_vars = {
    topic_arn = module.ingestor_images_output_topic.arn

    es_images_index    = local.es_images_index
    es_augmented_index = local.es_images_augmented_index

    # This var is read by the ingestor to switch the refresh interval off during a reindex
    # It appears to be unnecessary, as the database copes well enough, so it is currently
    # explicitly set to false, pending the removal of the switch.
    # Was var.reindexing_state.scale_up_tasks
    es_is_reindexing = false

    ingest_flush_interval_seconds = local.ingestor_images_flush_interval_seconds

    # We initially had this set to 100, and we saw errors like:
    #
    #     com.sksamuel.elastic4s.http.JavaClientExceptionWrapper:
    #     org.apache.http.ContentTooLongException: entity content is too long
    #     [130397743] for the configured buffer limit [104857600]
    #
    # My guess is that turning down the batch size will sort out these
    # errors, because I think this error is caused by getting a response
    # that's >100MB.
    #
    # I cranked it down to 50, still saw the error sometimes.
    #
    # See https://github.com/wellcomecollection/platform/issues/5038
    ingest_batch_size = 10
  }

  secret_env_vars = local.pipeline_storage_es_service_secrets["image_ingestor"]

  cpu    = 512
  memory = 4096

  min_capacity = var.min_capacity
  max_capacity = local.max_capacity

  fargate_service_boilerplate = local.fargate_service_boilerplate
}
