# -------------------------------------------------------
# RDS Snapshot Export to S3 – Step Functions state machine
#
# Triggered by EventBridge when an AWS Backup job completes
# for the id-minter vault. Can also be started manually via
# the console or CLI with a known recovery point ARN:
#
#   aws stepfunctions start-execution \
#     --state-machine-arn <arn> \
#     --input '{"id":"manual-2026-02-11","resources":["arn:aws:rds:eu-west-1:ACCOUNT:cluster-snapshot:awsbackup:job-XXXX"]}'
#
# The "id" field is used to build the ExportTaskIdentifier
# (id-exp-{id}), which must be <= 60 characters. When triggered
# by EventBridge the event ID (a UUID) is used automatically.
# -------------------------------------------------------

data "aws_caller_identity" "current" {}

# -------------------------------------------------------
# S3 bucket for id-minter exports
# -------------------------------------------------------

resource "aws_s3_bucket" "id_minter" {
  bucket = "wellcomecollection-platform-id-minter"
}

resource "aws_s3_bucket_lifecycle_configuration" "id_minter_exports" {
  bucket = aws_s3_bucket.id_minter.id

  rule {
    id     = "expire-old-exports"
    status = "Enabled"

    filter {
      prefix = "exports/"
    }

    expiration {
      days = 30
    }
  }
}

# -------------------------------------------------------
# KMS key – required by rds:StartExportTask
# -------------------------------------------------------

resource "aws_kms_key" "rds_export" {
  description             = "Encryption key for id-minter RDS S3 exports"
  deletion_window_in_days = 14
  enable_key_rotation     = true

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AllowKeyAdmin"
        Effect    = "Allow"
        Principal = { AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root" }
        Action    = "kms:*"
        Resource  = "*"
      },
      {
        Sid       = "AllowRDSExportRole"
        Effect    = "Allow"
        Principal = { AWS = aws_iam_role.rds_s3_export.arn }
        Action = [
          "kms:Encrypt",
          "kms:Decrypt",
          "kms:GenerateDataKey",
          "kms:GenerateDataKeyWithoutPlaintext",
          "kms:ReEncryptFrom",
          "kms:ReEncryptTo",
          "kms:CreateGrant",
          "kms:DescribeKey",
          "kms:RetireGrant",
        ]
        Resource = "*"
      },
      {
        Sid       = "AllowStepFunctionsRole"
        Effect    = "Allow"
        Principal = { AWS = aws_iam_role.export_state_machine.arn }
        Action = [
          "kms:DescribeKey",
          "kms:CreateGrant",
        ]
        Resource = "*"
      },
    ]
  })
}

resource "aws_kms_alias" "rds_export" {
  name          = "alias/id-minter-rds-export"
  target_key_id = aws_kms_key.rds_export.key_id
}

# -------------------------------------------------------
# IAM role assumed by the RDS export service
# -------------------------------------------------------

resource "aws_iam_role" "rds_s3_export" {
  name = "id-minter-rds-s3-export"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "export.rds.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "rds_s3_export_s3" {
  name = "s3-write"
  role = aws_iam_role.rds_s3_export.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:ListBucket",
          "s3:GetBucketLocation",
        ]
        Resource = [aws_s3_bucket.id_minter.arn]
      },
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject*",
          "s3:DeleteObject",
          "s3:AbortMultipartUpload",
        ]
        Resource = ["${aws_s3_bucket.id_minter.arn}/*"]
      },
    ]
  })
}

resource "aws_iam_role_policy" "rds_s3_export_kms" {
  name = "kms-encrypt"
  role = aws_iam_role.rds_s3_export.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "kms:Encrypt",
        "kms:Decrypt",
        "kms:GenerateDataKey",
        "kms:GenerateDataKeyWithoutPlaintext",
        "kms:ReEncryptFrom",
        "kms:ReEncryptTo",
        "kms:CreateGrant",
        "kms:DescribeKey",
        "kms:RetireGrant",
      ]
      Resource = [aws_kms_key.rds_export.arn]
    }]
  })
}

# -------------------------------------------------------
# SNS topic for export failure alerts
# -------------------------------------------------------

resource "aws_sns_topic" "rds_export_alerts" {
  name = "id-minter-rds-export-alerts"
}

# -------------------------------------------------------
# Step Functions state machine
# -------------------------------------------------------

locals {
  export_s3_bucket = aws_s3_bucket.id_minter.id

  export_state_machine_definition = jsonencode({
    Comment = "Export id-minter RDS backup snapshot to S3 as Parquet"
    StartAt = "StartExportTask"

    States = {
      StartExportTask = {
        Type     = "Task"
        Resource = "arn:aws:states:::aws-sdk:rds:startExportTask"
        Parameters = {
          "ExportTaskIdentifier.$" = "States.Format('id-exp-{}', $.id)"
          "SourceArn.$"            = "$.resources[0]"
          S3BucketName             = local.export_s3_bucket
          "S3Prefix.$"             = "States.Format('exports/{}/{}', $.detail.resourceName, States.ArrayGetItem(States.StringSplit($$.Execution.StartTime, 'T'), 0))"
          IamRoleArn               = aws_iam_role.rds_s3_export.arn
          KmsKeyId                 = aws_kms_key.rds_export.arn
        }
        ResultPath = "$.export"
        Next       = "WaitForExport"
        Retry = [
          {
            ErrorEquals     = ["States.ALL"]
            IntervalSeconds = 30
            MaxAttempts     = 3
            BackoffRate     = 2.0
          }
        ]
        Catch = [
          {
            ErrorEquals = ["States.ALL"]
            Next        = "NotifyFailure"
            ResultPath  = "$.error"
          }
        ]
      }

      WaitForExport = {
        Type    = "Wait"
        Seconds = 300
        Next    = "DescribeExportTask"
      }

      DescribeExportTask = {
        Type     = "Task"
        Resource = "arn:aws:states:::aws-sdk:rds:describeExportTasks"
        Parameters = {
          "ExportTaskIdentifier.$" = "$.export.ExportTaskIdentifier"
        }
        ResultSelector = {
          "ExportTaskIdentifier.$" = "$.ExportTasks[0].ExportTaskIdentifier"
          "Status.$"               = "$.ExportTasks[0].Status"
          "PercentProgress.$"      = "$.ExportTasks[0].PercentProgress"
        }
        ResultPath = "$.exportStatus"
        Next       = "CheckExportStatus"
        Retry = [
          {
            ErrorEquals     = ["States.ALL"]
            IntervalSeconds = 10
            MaxAttempts     = 3
            BackoffRate     = 2.0
          }
        ]
      }

      CheckExportStatus = {
        Type = "Choice"
        Choices = [
          {
            Variable     = "$.exportStatus.Status"
            StringEquals = "COMPLETE"
            Next         = "ExportSucceeded"
          },
          {
            Variable     = "$.exportStatus.Status"
            StringEquals = "FAILED"
            Next         = "NotifyFailure"
          },
          {
            Variable     = "$.exportStatus.Status"
            StringEquals = "CANCELED"
            Next         = "NotifyFailure"
          },
        ]
        Default = "WaitForExport"
      }

      ExportSucceeded = {
        Type = "Succeed"
      }

      NotifyFailure = {
        Type     = "Task"
        Resource = "arn:aws:states:::sns:publish"
        Parameters = {
          TopicArn    = aws_sns_topic.rds_export_alerts.arn
          Subject     = "id-minter RDS export failed"
          "Message.$" = "States.Format('Export task failed. Execution: {}', $$.Execution.Id)"
        }
        Next = "ExportFailed"
      }

      ExportFailed = {
        Type  = "Fail"
        Error = "ExportFailed"
        Cause = "RDS snapshot export to S3 failed – see SNS notification for details"
      }
    }
  })
}

resource "aws_cloudwatch_log_group" "export_state_machine" {
  name              = "/aws/stepfunctions/id-minter-rds-export"
  retention_in_days = 30
}

resource "aws_iam_role" "export_state_machine" {
  name = "id-minter-rds-export-state-machine"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "states.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "export_sfn_rds" {
  name = "rds-export"
  role = aws_iam_role.export_state_machine.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "rds:StartExportTask",
          "rds:DescribeExportTasks",
        ]
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = "iam:PassRole"
        Resource = aws_iam_role.rds_s3_export.arn
      },
      {
        Effect = "Allow"
        Action = [
          "kms:DescribeKey",
          "kms:CreateGrant",
        ]
        Resource = aws_kms_key.rds_export.arn
      },
    ]
  })
}

resource "aws_iam_role_policy" "export_sfn_sns" {
  name = "sns-publish"
  role = aws_iam_role.export_state_machine.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "sns:Publish"
      Resource = aws_sns_topic.rds_export_alerts.arn
    }]
  })
}

resource "aws_iam_role_policy" "export_sfn_logging" {
  name = "cloudwatch-logs"
  role = aws_iam_role.export_state_machine.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "logs:CreateLogDelivery",
        "logs:GetLogDelivery",
        "logs:UpdateLogDelivery",
        "logs:DeleteLogDelivery",
        "logs:ListLogDeliveries",
        "logs:PutResourcePolicy",
        "logs:DescribeResourcePolicies",
        "logs:DescribeLogGroups",
      ]
      Resource = "*"
    }]
  })
}

resource "aws_sfn_state_machine" "rds_export" {
  name       = "id-minter-rds-export"
  role_arn   = aws_iam_role.export_state_machine.arn
  definition = local.export_state_machine_definition

  logging_configuration {
    log_destination        = "${aws_cloudwatch_log_group.export_state_machine.arn}:*"
    include_execution_data = true
    level                  = "ERROR"
  }
}

# -------------------------------------------------------
# EventBridge – trigger on AWS Backup job completion
# -------------------------------------------------------

resource "aws_iam_role" "eventbridge_export" {
  name = "id-minter-rds-export-eventbridge"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "events.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "eventbridge_export_sfn" {
  name = "start-execution"
  role = aws_iam_role.eventbridge_export.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "states:StartExecution"
      Resource = aws_sfn_state_machine.rds_export.arn
    }]
  })
}

resource "aws_cloudwatch_event_rule" "backup_completed" {
  name        = "id-minter-backup-completed"
  description = "Fires when an AWS Backup job completes for the id-minter vault"

  event_pattern = jsonencode({
    source      = ["aws.backup"]
    detail-type = ["Backup Job State Change"]
    detail = {
      state           = ["COMPLETED"]
      backupVaultName = [aws_backup_vault.id_minter.name]
    }
  })
}

resource "aws_cloudwatch_event_target" "start_export" {
  rule     = aws_cloudwatch_event_rule.backup_completed.name
  arn      = aws_sfn_state_machine.rds_export.arn
  role_arn = aws_iam_role.eventbridge_export.arn
}
