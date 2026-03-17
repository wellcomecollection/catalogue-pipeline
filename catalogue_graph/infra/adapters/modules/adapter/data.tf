data "aws_region" "current" {}

data "aws_cloudwatch_event_bus" "event_bus" {
  name = "catalogue-pipeline-adapter-event-bus"
}

data "aws_s3_bucket" "adapter" {
  bucket = var.s3_bucket_name
}

data "terraform_remote_state" "platform_monitoring" {
  backend = "s3"
  config  = {
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
}
