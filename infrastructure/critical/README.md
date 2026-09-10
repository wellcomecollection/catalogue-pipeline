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

## Identifiers API read-only credential (id-minter)

The Identifiers API reads the registry as `identifiers_api_read`, a user with `SELECT` on one table, rather than as the master user. Terraform generates the password and holds it in the credential secret; creating the MySQL user is SQL, so `create_identifiers_api_user.sh` does that part and sets the user's password to match the secret.

After applying to a cluster with `data_api_consumer_role_arns` set, run the script with platform account credentials, passing the cluster and the secret terraform created:

```bash
./create_identifiers_api_user.sh \
  identifiers-v2-serverless-2026-07-03 \
  rds/identifiers-v2-serverless-2026-07-03/identifiers_api_read
```

It is safe to re-run, and it needs running for any other cluster the API reads. The password does not rotate on a schedule: to change it, replace `random_password.identifiers_api_read` in terraform, apply, and run the script again.
