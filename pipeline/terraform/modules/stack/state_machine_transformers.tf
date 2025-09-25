data "aws_cloudwatch_event_bus" "adapter_event_bus" {
  name = "catalogue-pipeline-adapter-event-bus"
}

locals {
  ebsco_transformer_lambda_index_date = "2025-09-04"
}

module "ebsco_transformer_lambda" {
  source = "git@github.com:wellcomecollection/terraform-aws-lambda?ref=v1.2.0"

  name        = "ebsco-adapter-transformer"
  description = "Lambda function to transform EBSCO data"
  runtime     = "python3.12"
  publish     = true

  filename = data.archive_file.empty_zip.output_path

  handler     = "steps.transformer.lambda_handler"
  memory_size = 4096
  timeout     = 600

  vpc_config = {
    subnet_ids = local.network_config.subnets
    security_group_ids = [
      aws_security_group.egress.id,
      local.network_config.ec_privatelink_security_group_id,
    ]
  }

  environment = {
    variables = {
      PIPELINE_DATE = var.pipeline_date
      // This is a hardcoded date for now, in order to test the new transformer against a fixed index
      INDEX_DATE    = local.ebsco_transformer_lambda_index_date
      S3_BUCKET     = local.ebsco_adapter_bucket
      S3_PREFIX     = "prod"
    }
  }
}

# Attach read-only Iceberg access policy to transformer lambda (now using s3tables read-only doc)
resource "aws_iam_role_policy" "transformer_lambda_iceberg_read" {
  role   = module.ebsco_transformer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.read_ebsco_adapter_s3tables_bucket.json
}

# Attach S3 read policy to transformer lambda
resource "aws_iam_role_policy" "transformer_lambda_s3_read" {
  role   = module.ebsco_transformer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.read_ebsco_adapter_bucket.json
}

# Attach S3 write policy to transformer lambda
resource "aws_iam_role_policy" "transformer_lambda_s3_write" {
  role   = module.ebsco_transformer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.write_ebsco_adapter_bucket.json
}

# Allow transformer to read pipeline storage secrets
resource "aws_iam_role_policy" "transformer_lambda_pipeline_storage_secret_read" {
  role   = module.ebsco_transformer_lambda.lambda_role.name
  policy = data.aws_iam_policy_document.read_ebsco_transformer_pipeline_storage_secrets.json
}


# State Machine Definition
locals {
  ebsco_transformer_state_machine_definition = jsonencode({
    StartAt = "TransformerStep"
    States = {
      TransformerStep = {
        Type     = "Task"
        Resource  = module.ebsco_transformer_lambda.lambda.arn
        InputPath = "$.detail"
        Next     = "EbscoIdMinterMap"
        Retry = [
          {
            ErrorEquals     = ["Lambda.ServiceException", "Lambda.AWSLambdaException", "Lambda.SdkClientException"]
            IntervalSeconds = 2
            MaxAttempts     = 3
            BackoffRate     = 2.0
          }
        ]
      }
      EbscoIdMinterMap = {
        Type                  = "Map"
        MaxConcurrency        = 2
        ToleratedFailureCount = 0
        ItemReader = {
          Resource = "arn:aws:states:::s3:getObject"
          ReaderConfig = {
            InputType = "JSONL"
          }
          Parameters = {
            "Bucket.$" = "$.successes.batch_file_location.bucket"
            "Key.$"    = "$.successes.batch_file_location.key"
          }
        }
        ItemSelector = {
          # Map item value is each JSON object line from the NDJSON file
          # Provide the event shape expected by the id_minter lambda StepFunctionMintingRequest
          "sourceIdentifiers.$" = "$$.Map.Item.Value.sourceIdentifiers"
          "jobId.$"             = "$.job_id"
        }
        ItemProcessor = {
          ProcessorConfig = {
            Mode          = "DISTRIBUTED"
            ExecutionType = "STANDARD"
          }
          StartAt = "IdMinterStep"
          States = {
            IdMinterStep = {
              Type     = "Task"
              Resource = module.id_minter_lambda_step_function.lambda_arn
              Retry = [
                {
                  ErrorEquals     = ["Lambda.ServiceException", "Lambda.AWSLambdaException", "Lambda.SdkClientException"]
                  IntervalSeconds = 2
                  MaxAttempts     = 3
                  BackoffRate     = 2.0
                }
              ]
              End = true
            }
          }
        }
        Next = "Success"
      }
      Success = {
        Type = "Succeed"
      }
    }
  })
}

module "ebsco_transformer_state_machine" {
  source = "../state_machine"

  name                     = "ebsco-transformer-${var.pipeline_date}"
  state_machine_definition = local.ebsco_transformer_state_machine_definition
  invokable_lambda_arns    = [
    module.ebsco_transformer_lambda.lambda.arn,
    module.id_minter_lambda_step_function.lambda_arn
  ]
  state_machine_iam_policy = data.aws_iam_policy_document.read_ebsco_adapter_bucket.json
}

# EventBridge trigger module for transformer - listens to adapter completion events
module "ebsco_transformer_trigger" {
  source = "../state_machine_trigger"

  name             = "ebsco-transformer-${var.pipeline_date}"
  event_bus_name   = data.aws_cloudwatch_event_bus.adapter_event_bus.name
  state_machine_arn = module.ebsco_transformer_state_machine.state_machine_arn
  event_pattern = {
    source       = ["ebsco.adapter"],
    "detail-type" = ["ebsco.adapter.completed"]
  }
  // Unfortunately the input template needs to be a full JSON object, 
  // so we must wrap the detail in another object and then unwrap in 
  // the state machine (it's not possible to just pass the detail directly)
  input_paths = {
    detail = "$.detail"
  }
  input_template = "{\"detail\": <detail>}"
}

output "ebsco_transformer_trigger_rule_name" {
  value       = module.ebsco_transformer_trigger.rule_name
  description = "Name of the EventBridge rule that triggers the transformer state machine"
}