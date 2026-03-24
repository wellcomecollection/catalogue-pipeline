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
      → On success: publish "Export Completed" event to EventBridge → state machine succeeds
      → On failure: SNS notification → state machine fails
```

## Terraform files

| File | Contents |
|---|---|
| `backup_id_minter.tf` | AWS Backup vault, plan (daily at 03:00 UTC), IAM role, and backup selection targeting the Aurora cluster |
| `export_id_minter.tf` | S3 bucket & lifecycle, Step Functions state machine, KMS key, RDS export IAM role, SNS alert topic, EventBridge event bus & trigger, CloudWatch log group |

## S3 output

Exports land in the bucket as Parquet files:

```
s3://wellcomecollection-platform-id-minter/
  exports/
    <cluster-name>/
      <date>/
        id-exp-<execution-id>/
          <table_name>/
            1/
              part-00000.gz.parquet
```

The `<cluster-name>` comes from the EventBridge event's `detail.resourceName` (e.g. `identifiers-v2-serverless`). The `<date>` is derived from the Step Functions execution start time.

Each export is encrypted with the `alias/id-minter-rds-export` KMS key.

Exports older than 30 days are automatically deleted by an S3 lifecycle rule.

## Manual trigger

The state machine expects three input fields:

- `id` — used to build the export task identifier (`id-exp-{id}`, max 60 chars)
- `resources[0]` — the snapshot/recovery point ARN to export
- `detail.resourceName` — the cluster name, used in the S3 prefix

```bash
aws stepfunctions start-execution \
  --state-machine-arn arn:aws:states:eu-west-1:760097843905:stateMachine:id-minter-rds-export \
  --input '{"id":"manual-2026-03-24","detail":{"resourceName":"identifiers-v2-serverless"},"resources":["arn:aws:rds:eu-west-1:760097843905:cluster-snapshot:awsbackup:job-XXXX"]}'
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
