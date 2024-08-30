resource "aws_ssm_parameter" "ebsco_adapter_ftp_server" {
  name        = "/catalogue_pipeline/ebsco_adapter/ftp_server"
  description = "The FTP server to connect to"
  type        = "String"
  value       = "placeholder"

  lifecycle {
    ignore_changes = [
      value
    ]
  }
}

resource "aws_ssm_parameter" "ebsco_adapter_ftp_username" {
  name        = "/catalogue_pipeline/ebsco_adapter/ftp_username"
  description = "The username to connect to the FTP server"
  type        = "String"
  value       = "placeholder"

  lifecycle {
    ignore_changes = [
      value
    ]
  }
}

resource "aws_ssm_parameter" "ebsco_adapter_ftp_password" {
  name        = "/catalogue_pipeline/ebsco_adapter/ftp_password"
  description = "The password to connect to the FTP server"
  type        = "SecureString"
  value       = "placeholder"

  lifecycle {
    ignore_changes = [
      value
    ]
  }
}

resource "aws_ssm_parameter" "ebsco_adapter_ftp_remote_dir" {
  name        = "/catalogue_pipeline/ebsco_adapter/ftp_remote_dir"
  description = "The remote directory to connect to on the FTP server"
  type        = "String"
  value       = "placeholder"

  lifecycle {
    ignore_changes = [
      value
    ]
  }
}

resource "aws_ssm_parameter" "ebsco_adapter_customer_id" {
  name        = "/catalogue_pipeline/ebsco_adapter/customer_id"
  description = "The customer ID to use when connecting to the FTP server"
  type        = "String"
  value       = "placeholder"

  lifecycle {
    ignore_changes = [
      value
    ]
  }
}

resource "aws_ssm_parameter" "ebsco_adapter_output_topic_arn" {
  name        = "/catalogue_pipeline/ebsco_adapter/output_topic_arn"
  description = "The ARN of the SNS topic to publish messages to"
  type        = "String"
  value       = module.ebsco_adapter_output_topic.arn
}

resource "aws_ssm_parameter" "ebsco_adapter_reindex_topic_arn" {
  name        = "/catalogue_pipeline/ebsco_adapter/reindex_topic_arn"
  description = "The ARN of the SNS topic to publish reindex messages to"
  type        = "String"
  value       = local.reindexer_topic_arn
}

resource "aws_ssm_parameter" "ebsco_adapter_bucket_name" {
  name        = "/catalogue_pipeline/ebsco_adapter/bucket_name"
  description = "The name of the S3 bucket to write files to"
  type        = "String"
  value       = aws_s3_bucket.ebsco_adapter.bucket
}
