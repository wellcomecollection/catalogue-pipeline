# Respinning a dated id-minter cluster from a production snapshot

How to rebuild a non-production id-minter RDS cluster (`identifiers-v2-serverless-<pipeline_date>`) from a fresh production snapshot, so a testing pipeline mints ids consistent with production. This procedure was used during the migration-testing clears (wellcomecollection/platform#6461 phase 3 and successors); these are the parts that are not obvious from the terraform.

## The change is declarative

Set `snapshot_identifier` in the module in `infrastructure/critical/rds_id_minter.tf` to a recovery point from `id-minter-backup-vault` (production backups land daily at 03:00 UTC, 30-day retention). This forces replacement of the cluster and its instance. The three endpoint secrets are replaced only at the version level, so the secret names the lambdas resolve are unchanged and just their values move.

Never merge a `snapshot_identifier` change that has not been applied. `infrastructure/critical` is shared and applied by hand, so a merged but unapplied change is triggered by the next person to apply the root for any reason, and it fails partway because deletion protection has to be lifted manually first (`aws rds modify-db-cluster --no-deletion-protection`).

## Steps that are not in terraform

- **`skip_final_snapshot` must already be in state before the replacement.** Both RDS modules take it (since wellcomecollection/catalogue-pipeline#3515) and it must be `true` on the dated cluster while it belongs to a test pipeline. Apply that as its own step first: the provider reads the flag from state at destroy time, so setting it in the same apply as the `snapshot_identifier` change still attempts a final snapshot and fails after deletion protection is already off. Revisit the flag if a dated cluster ever holds ids worth keeping.
- **`infrastructure/critical` must run via `./run_terraform.sh`**, which injects `EC_API_KEY` for the `ec` provider. Plain `terraform plan` dies with "Unable to create api Client config", and a `-target` plan can sidestep the provider and mislead you into thinking plain terraform works.
- **The restore drops `enable_http_endpoint`.** A snapshot restore does not carry the Data API setting. Nothing fails, because the lambdas use MySQL rather than the Data API, and the dated pipeline stack plans clean because the cluster lives in `infrastructure/critical`. Re-plan `infrastructure/critical` after the cluster reports available and apply the one-line fix.
- **Re-apply the dated pipeline stack afterwards.** The replacement issues a new managed master secret, and the id-minter and id-generator lambdas carry `RDS_SECRET_NAME` plus IAM scoped to the old ARN; until the apply runs (2 lambda environments, 4 IAM policies) they hold a dead reference.

## Verifying the restored data

Use `information_schema.TABLES.TABLE_ROWS` estimates rather than `SELECT COUNT(*)`, taken from both clusters so the comparison is like for like. A good restore looks like identical `canonical_ids` counts and an `identifiers` count a few hundred short of production, which is the growth since the snapshot was taken.
