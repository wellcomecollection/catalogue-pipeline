locals {
  name = "update_api_docs"
}
resource "aws_iam_instance_profile" "instance_profile" {
  name = "${local.name}_instance_profile"
  role = aws_iam_role.role.name
}

data "aws_iam_policy_document" "instance_policy" {
  statement {
    sid = "ecsInstanceRole"

    actions = [
      "ecs:StartTelemetrySession",
      "ecs:DeregisterContainerInstance",
      "ecs:DiscoverPollEndpoint",
      "ecs:Poll",
      "ecs:RegisterContainerInstance",
      "ecs:Submit*",
      "ecr:GetAuthorizationToken",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
    ]

    resources = [
      "*",
    ]
  }

  statement {
    sid = "allowLoggingToCloudWatch"

    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]

    resources = [
      "*",
    ]
  }
}

resource "aws_iam_role_policy" "instance" {
  name   = "${local.name}_instance_role_policy"
  role   = aws_iam_role.role.name
  policy = data.aws_iam_policy_document.instance_policy.json
}

data "aws_iam_policy_document" "assume_ec2_role" {
  statement {
    actions = [
      "sts:AssumeRole",
    ]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "role" {
  name               = "${local.name}_instance_role"
  assume_role_policy = data.aws_iam_policy_document.assume_ec2_role.json
}

resource "aws_iam_role" "task_role" {
  name               = "${local.name}_task_role"
  assume_role_policy = data.aws_iam_policy_document.assume_ecs_role.json
}

data "aws_iam_policy_document" "assume_ecs_role" {
  statement {
    actions = [
      "sts:AssumeRole",
    ]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "s3_role" {
  name   = "${local.name}_read_from_s3"
  role   = aws_iam_role.task_role.name
  policy = data.aws_iam_policy_document.allow_s3_read.json
}

# Our applications read their config from S3 on startup, make sure they
# have the appropriate read permissions.
# TODO: Scope these more tightly, possibly at the bucket or even object level.
data "aws_iam_policy_document" "allow_s3_read" {
  statement {
    actions = [
      "s3:Get*",
      "s3:List*",
    ]

    resources = [
      "*",
    ]
  }
}
