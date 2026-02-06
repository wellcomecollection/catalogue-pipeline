# -------------------------------------------------------
# AWS Backup – daily snapshot of the id-minter Aurora cluster
# -------------------------------------------------------

resource "aws_backup_vault" "id_minter" {
  name = "id-minter-backup-vault"
}

resource "aws_iam_role" "backup" {
  name = "id-minter-backup-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "backup.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "backup_service" {
  role       = aws_iam_role.backup.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSBackupServiceRolePolicyForBackup"
}

resource "aws_backup_plan" "id_minter_daily" {
  name = "id-minter-daily-backup"

  rule {
    rule_name         = "daily-snapshot"
    target_vault_name = aws_backup_vault.id_minter.name
    schedule          = "cron(0 3 * * ? *)" # 03:00 UTC daily

    lifecycle {
      delete_after = 30 # retain snapshots for 30 days
    }
  }
}

resource "aws_backup_selection" "id_minter" {
  name         = "id-minter-rds"
  iam_role_arn = aws_iam_role.backup.arn
  plan_id      = aws_backup_plan.id_minter_daily.id

  resources = [
    module.identifiers_serverless_rds_cluster.rds_cluster_arn
  ]
}
