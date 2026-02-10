# ID Minter RDS Backup & Export

Daily backup and Parquet export of the id-minter Aurora MySQL database to S3.

## Architecture

```
AWS Backup (daily 03:00 UTC)
  → Snapshot stored in backup vault (30-day retention)
  → EventBridge rule detects completion
    → Step Functions state machine
      → rds:StartExportTask (SDK integration, no Lambda)
      → Poll rds:DescribeExportTasks every 5 minutes
      → On success: export completes
      → On failure: SNS notification → state machine fails
```

## Terraform files

| File | Contents |
|---|---|
| `backup_id_minter.tf` | AWS Backup vault, plan (daily at 03:00 UTC), IAM role, and backup selection targeting the Aurora cluster |
| `export_id_minter.tf` | Step Functions state machine, KMS key, RDS export IAM role, SNS alert topic, EventBridge trigger |

## S3 output

Exports land in the existing bucket as Parquet files:

```
s3://wellcomecollection-platform-id-minter/
  exports/
    identifiers/
      2026-02-09/
        identifiers-export-<execution-name>/
          identifiers/
            <table_name>/
              1/
                part-00000.gz.parquet
```

The date is derived from the Step Functions execution start time.

Each export is encrypted with the `alias/id-minter-rds-export` KMS key.

## Manual trigger

The state machine reads the snapshot ARN from the `resources` array in the EventBridge event. For manual triggers, provide the recovery point ARN in the same field:

```bash
aws stepfunctions start-execution \
  --state-machine-arn arn:aws:states:eu-west-1:760097843905:stateMachine:id-minter-rds-export \
  --input '{"resources":["arn:aws:rds:eu-west-1:760097843905:cluster-snapshot:awsbackup:job-XXXX"]}'
```

To find available recovery points:

```bash
aws backup list-recovery-points-by-backup-vault \
  --backup-vault-name id-minter-backup-vault \
  --query 'RecoveryPoints[].{ARN:RecoveryPointArn,Created:CreationDate,Status:Status}' \
  --output table
```

## Failure alerts

Export failures are published to the `id-minter-rds-export-alerts` SNS topic.
