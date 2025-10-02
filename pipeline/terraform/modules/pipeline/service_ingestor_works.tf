locals {
  # The flush interval must be significantly lower than the cooldown period
  # for the scaling down alarm (1 minute).
  # Fargate services take 60-90s to start up.
  # Assuming the worst - that scaling up on detecting a message in the queue took
  # 90s, that gives the ingestor a maximum of 30 seconds to finish working or
  # it will be terminated prematurely.
  # It is still a possibility that messages will fail, if they are received after the first flush
  # and less than the flush period before the scale down happens, but by keeping this number
  # very low, this can be prevented.
  ingestor_works_flush_interval_seconds = 10
}

module "ingestor_works_output_topic" {
  source = "git::github.com/wellcomecollection/terraform-aws-sns-topic.git//?ref=v1.0.1"
  name   = "${local.namespace}_ingestor_works_output"
  # Allow the catalogue account to subscribe to works being ingested.
  # The Concepts Aggregator needs this access.
  cross_account_subscription_ids = ["756629837203"]
}


resource "aws_iam_role_policy" "worker_role_can_publish_sns" {
  role   = module.ingestor_works.task_role_name
  policy = module.ingestor_works_output_topic.publish_policy
}

module "ingestor_works" {
  source = "../fargate_service"

  name            = "ingestor_works"
  container_image = local.ingestor_works_image

  topic_arns = [
    module.merger_works_output_topic.arn,
    module.relation_embedder_sub.relation_embedder_output_topic_arn
  ]

  # The ingestor_works_flush_interval_seconds will wait for up to ingestor_works_flush_interval_seconds
  # before starting to process any of the messages it has pulled from the queue.
  # This is to allow it to run over efficiently sized batches, rather than processing everything one at a time.
  # Allow a buffer on top of that for the processor to actually do work, before declaring a message dead.
  queue_visibility_timeout_seconds = local.ingestor_works_flush_interval_seconds + 30

  # In normal running, messages need to be kicked off the main queue as soon as possible
  # if things start slowing down.
  # During the overnight Sierra Harvest, the ingestor slows down to the extent that the oldest message
  # normally sits for up to about 4000 seconds, and sometimes up to about 8000.
  # During a reindex, everything is processed much more quickly (ca. 600s max), so there is no need to
  # configure this value separately for the two scenarios

  # This number may be subject to some refinement.  This is a bit under three hours, which, when considered
  # alongside the 0200 Sierra Harvest (which is the situation where runaway ingestion has happened),
  # means that it should start clearing down before 0500, with plenty of time before the lion's share
  # of website users get up.
  message_retention_seconds = 10000

  # If, at any point, message processing slows down enough to take longer than queue_visibility_timeout_seconds (90),
  # the retry facility will make things worse.
  # The ingestor will have received the message, and be trying to process it, but SQS will declare it lost, and
  # ask for it to be processed again.  As a result, if this system goes over capacity to this extent, each message
  # will be processed max_receive_count times, which means going even further over capacity
  # As with message_retention, there is no need to maintain two different configurations. In normal running,
  # we want to protect the database from overloading by pushing messages onto the DLQ on first failure.
  # During a reindex, it is under human supervision, and we can easily redrive the DLQ if required (and often do).

  max_receive_count = var.reindexing_state.scale_up_tasks ? 3 : 1

  env_vars = {
    topic_arn = module.ingestor_works_output_topic.arn

    es_works_index        = local.es_works_index
    es_denormalised_index = local.es_works_denormalised_index

    ingest_flush_interval_seconds = local.ingestor_works_flush_interval_seconds

    # When an ingestor retrieves a batch from the denormalised index,
    # it might OutOfMemoryError when it deserialises the JSON into
    # in-memory Works.
    #
    # This happens when the collection has a lot of large Works,
    # i.e. Works with lots of relations.
    #
    # During a reindex, the batch is likely a mix of deleted/suppressed Works
    # (small) and visible Works (small to large), so it's unlikely to throw
    # an OOM error.  If it does, the service can restart and the next batch
    # will have a different distribution of Works.
    #
    # When we're not reindexing, a batch of large Works can potentially
    # gum up the ingestor: it throws an OOM, the messages get retried,
    # it throws another OOM.  Continue until messages go to the DLQ.
    #
    # To avoid this happening, we reduce the batch size when we're not
    # reindexing.  A smaller batch size is a bit less efficient, but
    # we don't process many messages when not reindexing so this is fine.
    ingest_batch_size = var.reindexing_state.scale_up_tasks ? 100 : 10
  }

  secret_env_vars = local.pipeline_storage_es_service_secrets["work_ingestor"]

  cpu    = 2048
  memory = 8192

  min_capacity = var.min_capacity
  max_capacity = local.max_capacity

  fargate_service_boilerplate = local.fargate_service_boilerplate
}
