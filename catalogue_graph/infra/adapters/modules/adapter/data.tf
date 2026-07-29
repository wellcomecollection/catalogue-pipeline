data "aws_region" "current" {}

data "aws_cloudwatch_event_bus" "event_bus" {
  name = var.event_bus_name
}

data "aws_s3_bucket" "adapter" {
  bucket = var.s3_bucket_name
}

data "terraform_remote_state" "platform_monitoring" {
  backend = "s3"
  config = {
    assume_role = {
      role_arn = "arn:aws:iam::760097843905:role/platform-read_only"
    }
    bucket = "wellcomecollection-platform-infra"
    key    = "terraform/monitoring.tfstate"
    region = "eu-west-1"
  }
}

locals {
  chatbot_topic_arn = data.terraform_remote_state.platform_monitoring.outputs.chatbot_topic_arn
  steps_namespace   = coalesce(var.steps_namespace, var.namespace)

  # The oai_pmh steps package carries the mark-published step and the
  # published-cursor trigger; adapters on other steps packages (e.g. ebsco)
  # have no OAI-PMH window store to stamp.
  published_tracking = local.steps_namespace == "oai_pmh"

  # Only the oai_pmh loader accepts an id-list event. The
  # ebsco loader takes its own event, so it gets no id-mode branch rather than
  # one that routes to a loader unable to parse it.
  id_mode_enabled = local.steps_namespace == "oai_pmh"
}
