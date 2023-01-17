module "reindex_jobs_topic" {
  source = "github.com/wellcomecollection/terraform-aws-sns-topic.git?ref=v1.0.0"
  name   = "reindex_worker_jobs"
}
