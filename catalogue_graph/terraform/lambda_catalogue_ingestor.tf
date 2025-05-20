module "ingestor_indexer_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "catalogue-graph-ingestor-indexer"
  description = "Indexes catalogue concepts into elasticsearch"
  runtime     = "python3.13"
  publish     = true

  // New versions are automatically deployed through a GitHub action.
  // To deploy manually, see `scripts/deploy_lambda_zip.sh`
  filename = data.archive_file.empty_zip.output_path

  handler     = "ingestor_indexer.lambda_handler"
  memory_size = 1024
  timeout     = 300

  vpc_config = {
    subnet_ids = local.private_subnets
    security_group_ids = [
      aws_security_group.egress.id,
      local.ec_privatelink_security_group_id
    ]
  }

  environment = {
    variables = {
      INGESTOR_S3_BUCKET = aws_s3_bucket.catalogue_graph_bucket.bucket
      INGESTOR_S3_PREFIX = "ingestor"
    }
  }
}

resource "aws_iam_role_policy" "ingestor_indexer_lambda_read_secrets_policy" {
  role   = module.ingestor_indexer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_secret_read.json
}

resource "aws_iam_role_policy" "ingestor_indexer_lambda_read_pipeline_secrets_policy" {
  role   = module.ingestor_indexer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_pipeline_storage_secret_read.json
}

resource "aws_iam_role_policy" "ingestor_indexer_lambda_s3_read_policy" {
  role   = module.ingestor_indexer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_read.json
}

module "ingestor_loader_monitor_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "catalogue-graph-ingestor-loader-monitor"
  description = "Monitors the output of ingestor_loader lambda"
  runtime     = "python3.13"
  publish     = true

  // New versions are automatically deployed through a GitHub action.
  // To deploy manually, see `scripts/deploy_lambda_zip.sh`
  filename = data.archive_file.empty_zip.output_path

  handler     = "ingestor_loader_monitor.lambda_handler"
  memory_size = 1024
  timeout     = 300

  vpc_config = {
    subnet_ids = local.private_subnets
    security_group_ids = [
      aws_security_group.egress.id,
    ]
  }

  environment = {
    variables = {
      INGESTOR_S3_BUCKET = aws_s3_bucket.catalogue_graph_bucket.bucket
      INGESTOR_S3_PREFIX = "ingestor"
    }
  }
}

resource "aws_iam_role_policy" "ingestor_loader_monitor_lambda_s3_write_policy" {
  role   = module.ingestor_loader_monitor_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_write.json
}

resource "aws_iam_role_policy" "ingestor_loader_monitor_lambda_s3_read_policy" {
  role   = module.ingestor_loader_monitor_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_read.json
}

resource "aws_iam_role_policy" "ingestor_loader_monitor_lambda_cloudwatch_write_policy" {
  role   = module.ingestor_loader_monitor_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.cloudwatch_write.json
}

module "ingestor_loader_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "catalogue-graph-ingestor-loader"
  description = "Loads catalogue concepts into S3 from Neptune"
  runtime     = "python3.13"
  publish     = true

  // New versions are automatically deployed through a GitHub action.
  // To deploy manually, see `scripts/deploy_lambda_zip.sh`
  filename = data.archive_file.empty_zip.output_path

  handler     = "ingestor_loader.lambda_handler"
  memory_size = 1024
  timeout     = 600

  vpc_config = {
    subnet_ids = local.private_subnets
    security_group_ids = [
      aws_security_group.egress.id,
      aws_security_group.neptune_service_security_group.id
    ]
  }

  environment = {
    variables = {
      INGESTOR_S3_BUCKET = aws_s3_bucket.catalogue_graph_bucket.bucket
      INGESTOR_S3_PREFIX = "ingestor"
    }
  }
}

resource "aws_iam_role_policy" "ingestor_loader_lambda_read_secrets_policy" {
  role   = module.ingestor_loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_secret_read.json
}

resource "aws_iam_role_policy" "ingestor_loader_lambda_s3_read_policy" {
  role   = module.ingestor_loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_read.json
}

resource "aws_iam_role_policy" "ingestor_loader_lambda_s3_write_policy" {
  role   = module.ingestor_loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_write.json
}

resource "aws_iam_role_policy" "ingestor_loader_lambda_neptune_read_policy" {
  role   = module.ingestor_loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.neptune_read.json
}

module "ingestor_trigger_monitor_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "catalogue-graph-ingestor-trigger-monitor"
  description = "Monitors the output of ingestor_trigger lambda"
  runtime     = "python3.13"
  publish     = true

  // New versions are automatically deployed through a GitHub action.
  // To deploy manually, see `scripts/deploy_lambda_zip.sh`
  filename = data.archive_file.empty_zip.output_path

  handler     = "ingestor_trigger_monitor.lambda_handler"
  memory_size = 1024
  timeout     = 300

  vpc_config = {
    subnet_ids = local.private_subnets
    security_group_ids = [
      aws_security_group.egress.id,
    ]
  }

  environment = {
    variables = {
      INGESTOR_S3_BUCKET = aws_s3_bucket.catalogue_graph_bucket.bucket
      INGESTOR_S3_PREFIX = "ingestor"
    }
  }
}

resource "aws_iam_role_policy" "ingestor_trigger_monitor_lambda_s3_write_policy" {
  role   = module.ingestor_trigger_monitor_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_write.json
}

resource "aws_iam_role_policy" "ingestor_trigger_monitor_lambda_s3_read_policy" {
  role   = module.ingestor_trigger_monitor_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_read.json
}

resource "aws_iam_role_policy" "ingestor_trigger_monitor_cloudwatch_write_policy" {
  role   = module.ingestor_trigger_monitor_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.cloudwatch_write.json
}

module "ingestor_trigger_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "catalogue-graph-ingestor-trigger"
  description = "Triggers the ingestor lambdas"
  runtime     = "python3.13"
  publish     = true

  // New versions are automatically deployed through a GitHub action.
  // To deploy manually, see `scripts/deploy_lambda_zip.sh`
  filename = data.archive_file.empty_zip.output_path

  handler     = "ingestor_trigger.lambda_handler"
  memory_size = 1024
  timeout     = 300

  vpc_config = {
    subnet_ids = local.private_subnets
    security_group_ids = [
      aws_security_group.egress.id,
      aws_security_group.neptune_service_security_group.id
    ]
  }

  environment = {
    variables = {
      INGESTOR_SHARD_SIZE = 10000
    }
  }
}

resource "aws_iam_role_policy" "ingestor_trigger_lambda_neptune_read_policy" {
  role   = module.ingestor_trigger_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.neptune_read.json
}

resource "aws_iam_role_policy" "ingestor_trigger_lambda_read_secrets_policy" {
  role   = module.ingestor_trigger_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_secret_read.json
}
