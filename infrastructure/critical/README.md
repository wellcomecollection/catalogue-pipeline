# Catalogue Critical Infrastructure

Contains critical infrastructure for the catalogue services

## Running terraform

In order to run terraform the Elastic Cloud terraform provider requires that the EC_API_KEY environment variable be set.

You can run `run_terraform.sh` to set the correct environment variable and run terraform. Any parameters you pass to that script will be passed to `terraform`.

The script will by default set AWS_PROFILE to "platform", but you can use a different value by setting AWS_PROFILE yourself.

## RDS Snapshot Export (id-minter)

The `export_id_minter.tf` file defines a Step Functions state machine that exports id-minter RDS backup snapshots to S3 as Parquet. It is triggered automatically by EventBridge when an AWS Backup job completes.

To start an export manually, you need to provide both an `id` (used to build the `ExportTaskIdentifier`) and the snapshot ARN in `resources`:

```bash
aws stepfunctions start-execution \
  --state-machine-arn <arn> \
  --input '{"id":"manual-2026-02-11","resources":["arn:aws:rds:eu-west-1:ACCOUNT:cluster-snapshot:awsbackup:job-XXXX"]}'
```

The `id` field must contain only letters, digits, and hyphens, and the resulting `ExportTaskIdentifier` (`id-exp-{id}`) must be at most 60 characters. When triggered by EventBridge, the event ID (a UUID) is used automatically.
