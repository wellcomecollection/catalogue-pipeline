# State Machine Definition
locals {
  ebsco_transformer_state_machine_definition = jsonencode({
    StartAt = "EbscoIdMinterMap"
    States = {
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
            "Bucket.$" = "$.batch_file_bucket"
            "Key.$"    = "$.batch_file_key"
          }
        }
        ItemSelector = {
          # Map item value is each JSON object line from the NDJSON file
          # Provide the event shape expected by the id_minter lambda StepFunctionMintingRequest
          "sourceIdentifiers.$" = "$$.Map.Item.Value.ids"
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

data "aws_s3_bucket" "ebsco_adapter_bucket" {
  bucket = "wellcomecollection-platform-ebsco-adapter"
}

# IAM policy allowing the state machine to read from S3 bucket
data "aws_iam_policy_document" "state_machine_s3_read_policy" {
  statement {
    actions = [
      "s3:GetObject",
      "s3:ListBucket"
    ]
    resources = [
      "${data.aws_s3_bucket.ebsco_adapter_bucket.arn}/prod/batches/*",
      "${data.aws_s3_bucket.ebsco_adapter_bucket.arn}"
    ]
  }
}

module "ebsco_transformer_state_machine" {
  source = "../state_machine"

  name                     = "ebsco-transformer-${var.pipeline_date}"
  state_machine_definition = local.ebsco_transformer_state_machine_definition
  invokable_lambda_arns    = [module.id_minter_lambda_step_function.lambda_arn]
  state_machine_iam_policy = data.aws_iam_policy_document.state_machine_s3_read_policy.json
}