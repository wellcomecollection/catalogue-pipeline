resource "aws_iam_role" "pipe_role" {
  name = "${var.name}-pipe-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "pipes.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_policy" "pipe_source_policy" {
  name = "${var.name}-pipe-source-policy"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes"]
      Resource = module.input_queue.arn
    }]
  })
}

resource "aws_iam_policy" "pipe_target_policy" {
  name = "${var.name}-pipe-target-policy"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["states:StartExecution"]
      Resource = var.state_machine_arn
    }]
  })
}

resource "aws_iam_role_policy_attachment" "pipe_source_attachment" {
  role       = aws_iam_role.pipe_role.name
  policy_arn = aws_iam_policy.pipe_source_policy.arn
}

resource "aws_iam_role_policy_attachment" "pipe_target_attachment" {
  role       = aws_iam_role.pipe_role.name
  policy_arn = aws_iam_policy.pipe_target_policy.arn
}
