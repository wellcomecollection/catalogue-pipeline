resource "aws_iam_role" "state_machine_execution_role" {
  name               = "catalogue-graph-state-machine-execution-role"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17",
    Statement = [
      {
        Effect    = "Allow",
        Principal = {
          Service = "states.amazonaws.com"
        },
        Action = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_policy" "state_machine_policy" {
  policy = jsonencode({
    Version   = "2012-10-17",
    Statement = [
      {
        Effect   = "Allow",
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"],
        Resource = "*"
      },
      {
        Effect   = "Allow",
        Action   = ["lambda:InvokeFunction"],
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "sfn_policy_attachment" {
  role       = aws_iam_role.state_machine_execution_role.name
  policy_arn = aws_iam_policy.state_machine_policy.arn
}

resource "aws_sfn_state_machine" "catalogue_graph_pipeline" {
  name     = "catalogue-graph-pipeline"
  role_arn = aws_iam_role.state_machine_execution_role.arn

  definition = jsonencode({
    Comment = "Extract raw concepts, transform them into nodes and edges, and stream them into an S3 bucket."
    StartAt = "Extractors"
    States  = merge(local.extractor_states, local.bulk_loader_states, local.success_state)
  })
}

locals {
  extractor_states = {
    # Run all extractor Lambda functions in parallel.
    Extractors = {
      Type     = "Parallel"
      Branches = flatten([
        for index, task_input in var.state_machine_inputs : {
          StartAt = "Extract ${task_input.label}"
          States  = {
            "Extract ${task_input.label}" = {
              Type       = "Task"
              Resource   = module.extractor_lambda.lambda.arn
              Parameters = {
                "transformer_type"   = task_input.transformer_type,
                "entity_type"        = task_input.entity_type,
                "stream_destination" = "s3",
                "sample_size"        = 1000 # Only stream a small sample while testing
              }
              End = true
            }
          }
        }
      ])
      Next = "Load ${var.state_machine_inputs[0].label}"
    }
  }

  bulk_loader_states = tomap({
    for index, task_input in var.state_machine_inputs :
    "Load ${task_input.label}" => {
      Type       = "Task"
      Resource   = module.bulk_loader_lambda.lambda.arn,
      Parameters = {
        "transformer_type" = task_input.transformer_type,
        "entity_type"      = task_input.entity_type
      }
      Next = index == length(var.state_machine_inputs) - 1 ? "Success" : "Load ${var.state_machine_inputs[index + 1].label}"
    }
  })

  success_state = tomap({
    Success = {
      Type = "Succeed"
    }
  })
}

