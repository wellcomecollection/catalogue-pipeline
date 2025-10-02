// lambdas used in the ingestor state machine

// New versions of the lambdas are automatically deployed through a GitHub action.
// To deploy manually, see `scripts/deploy_lambda_zip.sh`

# ingestor_loader
module "ingestor_loader_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "catalogue-graph-ingestor-loader"
  description  = "Loads catalogue concepts into S3 from Neptune"
  package_type = "Image"
  image_uri    = "${aws_ecr_repository.unified_pipeline_lambda.repository_url}:prod"
  publish      = true

  image_config = {
    command = ["ingestor.steps.ingestor_loader.lambda_handler"]
  }

  memory_size = 4096
  timeout     = 900

  vpc_config = {
    subnet_ids = local.private_subnets
    security_group_ids = [
      aws_security_group.egress.id,
      aws_security_group.neptune_service_security_group.id,
      local.ec_privatelink_security_group_id
    ]
  }

  environment = {
    variables = {
      CATALOGUE_GRAPH_S3_BUCKET = aws_s3_bucket.catalogue_graph_bucket.bucket
      INGESTOR_S3_PREFIX        = "ingestor"
    }
  }
}

resource "aws_iam_role_policy" "ingestor_loader_lambda_read_secrets_policy" {
  role   = module.ingestor_loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_secret_read.json
}

resource "aws_iam_role_policy" "ingestor_loader_lambda_read_pipeline_secrets_policy" {
  role   = module.ingestor_loader_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_allow_pipeline_storage_secret_read.json
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


# ingestor_loader_monitor
module "ingestor_loader_monitor_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "catalogue-graph-ingestor-loader-monitor"
  description  = "Monitors the output of ingestor_loader lambda"
  package_type = "Image"
  image_uri    = "${aws_ecr_repository.unified_pipeline_lambda.repository_url}:prod"
  publish      = true

  image_config = {
    command = ["ingestor.steps.ingestor_loader_monitor.lambda_handler"]
  }

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
      CATALOGUE_GRAPH_S3_BUCKET = aws_s3_bucket.catalogue_graph_bucket.bucket
      INGESTOR_S3_PREFIX        = "ingestor"
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


# ingestor_indexer
module "ingestor_indexer_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "catalogue-graph-ingestor-indexer"
  description  = "Indexes catalogue concepts into elasticsearch"
  package_type = "Image"
  image_uri    = "${aws_ecr_repository.unified_pipeline_lambda.repository_url}:prod"
  publish      = true

  image_config = {
    command = ["ingestor.steps.ingestor_indexer.lambda_handler"]
  }

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
      CATALOGUE_GRAPH_S3_BUCKET = aws_s3_bucket.catalogue_graph_bucket.bucket
      INGESTOR_S3_PREFIX        = "ingestor"
    }
  }
}

resource "aws_iam_role_policy" "ingestor_indexer_lambda_read_secrets_policy" {
  role   = module.ingestor_indexer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_secret_read.json
}

resource "aws_iam_role_policy" "ingestor_indexer_lambda_read_pipeline_secrets_policy" {
  role   = module.ingestor_indexer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_allow_pipeline_storage_secret_read.json
}

resource "aws_iam_role_policy" "ingestor_indexer_lambda_s3_read_policy" {
  role   = module.ingestor_indexer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_read.json
}

# ingestor_indexer_monitor
module "ingestor_indexer_monitor_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "catalogue-graph-ingestor-indexer-monitor"
  description  = "Monitors the output of ingestor_indexer lambda"
  package_type = "Image"
  image_uri    = "${aws_ecr_repository.unified_pipeline_lambda.repository_url}:prod"
  publish      = true

  image_config = {
    command = ["ingestor.steps.ingestor_indexer_monitor.lambda_handler"]
  }

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
      CATALOGUE_GRAPH_S3_BUCKET = aws_s3_bucket.catalogue_graph_bucket.bucket
      INGESTOR_S3_PREFIX        = "ingestor"
    }
  }
}

resource "aws_iam_role_policy" "ingestor_indexer_monitor_lambda_s3_write_policy" {
  role   = module.ingestor_indexer_monitor_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_write.json
}

resource "aws_iam_role_policy" "ingestor_indexer_monitor_lambda_s3_read_policy" {
  role   = module.ingestor_indexer_monitor_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_read.json
}

resource "aws_iam_role_policy" "ingestor_indexer_monitor_lambda_cloudwatch_write_policy" {
  role   = module.ingestor_indexer_monitor_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.cloudwatch_write.json
}


#ingestor_deletions
module "ingestor_deletions_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "catalogue-graph-ingestor-deletions"
  description  = "Removes concepts which no longer exist in the catalogue graph from the Elasticsearch index."
  package_type = "Image"
  image_uri    = "${aws_ecr_repository.unified_pipeline_lambda.repository_url}:prod"
  publish      = true

  image_config = {
    command = ["ingestor.steps.ingestor_deletions.lambda_handler"]
  }

  memory_size = 1024
  timeout     = 60 // 1 minute

  vpc_config = {
    subnet_ids = local.private_subnets
    security_group_ids = [
      aws_security_group.egress.id,
      local.ec_privatelink_security_group_id
    ]
  }

  #  error_alarm_topic_arn = data.terraform_remote_state.monitoring.outputs["platform_lambda_error_alerts_topic_arn"]
}

resource "aws_iam_role_policy" "ingestor_deletions_lambda_read_secrets_policy" {
  role   = module.ingestor_deletions_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_secret_read.json
}

resource "aws_iam_role_policy" "ingestor_deletions_lambda_read_pipeline_secrets_policy" {
  role   = module.ingestor_deletions_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_allow_pipeline_storage_secret_read.json
}

# Allow the Lambda to write the 'report.ingestor_deletions.json' file
resource "aws_iam_role_policy" "ingestor_deletions_lambda_s3_write_policy" {
  role   = module.ingestor_deletions_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_write.json
}

resource "aws_iam_role_policy" "ingestor_deletions_lambda_s3_read_policy" {
  role   = module.ingestor_deletions_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_read.json
}

# Read files outputted by the graph_remover Lambda
resource "aws_iam_role_policy" "ingestor_deletions_lambda_s3_policy" {
  role   = module.ingestor_deletions_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_deletions_s3_policy.json
}


# ingestor_reporter

module "ingestor_reporter_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name         = "catalogue-graph-ingestor-reporter"
  description  = "Generates a report on the latest pipeline run and posts it to #wc-search-alerts"
  package_type = "Image"
  image_uri    = "${aws_ecr_repository.unified_pipeline_lambda.repository_url}:prod"
  publish      = true

  // New versions are automatically deployed through a GitHub action.
  // To deploy manually, see `scripts/deploy_lambda_zip.sh`

  image_config = {
    command = ["ingestor.steps.ingestor_reporter.lambda_handler"]
  }

  memory_size = 128
  timeout     = 300

  environment = {
    variables = {
      CATALOGUE_GRAPH_S3_BUCKET = aws_s3_bucket.catalogue_graph_bucket.bucket
      INGESTOR_S3_PREFIX        = "ingestor"
      SLACK_SECRET_ID           = local.slack_webhook
    }
  }
}

resource "aws_iam_role_policy" "ingestor_reporter_lambda_read_slack_secret_policy" {
  role   = module.ingestor_reporter_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.allow_slack_secret_read.json
}

resource "aws_iam_role_policy" "ingestor_reporter_lambda_s3_read_policy" {
  role   = module.ingestor_reporter_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.ingestor_s3_read.json
}
