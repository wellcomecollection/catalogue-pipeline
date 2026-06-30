# The EC2 task (Python inference_manager + the three inferrer sidecars) that the
# image-inferrer state machine launches via runTask.waitForTaskToken. This is the
# sole image inferrer; the old SQS-driven Scala service has been retired. The inferrer
# port and CPU/memory sizing locals below were previously shared with that service and
# now live here, its only remaining consumer.

data "aws_caller_identity" "current" {}

data "aws_ecr_repository" "unified_pipeline_task" {
  name = "uk.ac.wellcome/unified_pipeline_task"
}

locals {
  inference_manager_container_name = "inference-manager-${var.pipeline_date}"
  secrets_manager_prefix           = "arn:aws:secretsmanager:eu-west-1:${data.aws_caller_identity.current.account_id}:secret"

  feature_inferrer_port      = 3141
  palette_inferrer_port      = 3142
  aspect_ratio_inferrer_port = 3143
  shared_storage_name        = "shared_storage"
  shared_storage_path        = "/data"

  # This is the CPU/memory available on an ECS instance which isn't running
  # any tasks.  You can find it in the ECS console, in the list of
  # capacity providers.
  base_2x_total_cpu = 8192
  base_1x_total_cpu = 4096

  base_2x_total_memory = 14336
  base_1x_total_memory = 7168

  base_manager_memory      = 2048
  base_manager_cpu         = 1024
  base_aspect_ratio_cpu    = 2048
  base_aspect_ratio_memory = 2048

  # When we're not reindexing, we halve the size of these tasks, because
  # they won't be getting as many updates.
  total_cpu           = var.reindexing_state.scale_up_tasks ? local.base_2x_total_cpu : local.base_1x_total_cpu
  total_memory        = var.reindexing_state.scale_up_tasks ? local.base_2x_total_memory : local.base_1x_total_memory
  manager_memory      = var.reindexing_state.scale_up_tasks ? local.base_manager_memory : floor(local.base_manager_memory / 2)
  manager_cpu         = var.reindexing_state.scale_up_tasks ? local.base_manager_cpu : floor(local.base_manager_cpu / 2)
  aspect_ratio_cpu    = var.reindexing_state.scale_up_tasks ? local.base_aspect_ratio_cpu : floor(local.base_aspect_ratio_cpu / 2)
  aspect_ratio_memory = var.reindexing_state.scale_up_tasks ? local.base_aspect_ratio_memory : floor(local.base_aspect_ratio_memory / 2)

  log_router_memory = 50

  inferrer_cpu    = floor(0.5 * (local.total_cpu - local.manager_cpu - local.aspect_ratio_cpu))
  inferrer_memory = floor(0.5 * (local.total_memory - local.manager_memory - local.aspect_ratio_memory - local.log_router_memory))
}

module "inference_manager_ecs_task" {
  source    = "../inferrer_ecs_task"
  task_name = local.inference_manager_container_name

  manager_image  = "${data.aws_ecr_repository.unified_pipeline_task.repository_url}:env.${var.pipeline_date}"
  manager_cpu    = local.manager_cpu
  manager_memory = local.manager_memory

  manager_env_vars = {
    FEATURE_INFERRER_HOST      = "localhost"
    FEATURE_INFERRER_PORT      = local.feature_inferrer_port
    PALETTE_INFERRER_HOST      = "localhost"
    PALETTE_INFERRER_PORT      = local.palette_inferrer_port
    ASPECT_RATIO_INFERRER_HOST = "localhost"
    ASPECT_RATIO_INFERRER_PORT = local.aspect_ratio_inferrer_port
    IMAGES_ROOT                = local.shared_storage_path
  }

  manager_mount_points = [
    {
      containerPath = local.shared_storage_path
      sourceVolume  = local.shared_storage_name
    }
  ]

  volumes = [
    {
      name      = local.shared_storage_name
      host_path = null
    }
  ]

  apps = {
    feature_inferrer = {
      image           = local.feature_inferrer_image
      cpu             = local.inferrer_cpu
      memory          = local.inferrer_memory
      env_vars        = { PORT = local.feature_inferrer_port }
      secret_env_vars = {}
      mount_points = [
        { containerPath = local.shared_storage_path, sourceVolume = local.shared_storage_name }
      ]
      healthcheck = {
        command     = ["CMD-SHELL", "curl -f http://localhost:${local.feature_inferrer_port}/healthcheck"]
        interval    = 30
        retries     = 3
        startPeriod = 30
        timeout     = 5
      }
    }
    palette_inferrer = {
      image           = local.palette_inferrer_image
      cpu             = local.inferrer_cpu
      memory          = local.inferrer_memory
      env_vars        = { PORT = local.palette_inferrer_port }
      secret_env_vars = {}
      mount_points = [
        { containerPath = local.shared_storage_path, sourceVolume = local.shared_storage_name }
      ]
      healthcheck = {
        command     = ["CMD-SHELL", "curl -f http://localhost:${local.palette_inferrer_port}/healthcheck"]
        interval    = 30
        retries     = 3
        startPeriod = 30
        timeout     = 5
      }
    }
    aspect_ratio_inferrer = {
      image           = local.aspect_ratio_inferrer_image
      cpu             = local.aspect_ratio_cpu
      memory          = local.aspect_ratio_memory
      env_vars        = { PORT = local.aspect_ratio_inferrer_port }
      secret_env_vars = {}
      mount_points = [
        { containerPath = local.shared_storage_path, sourceVolume = local.shared_storage_name }
      ]
      healthcheck = {
        command     = ["CMD-SHELL", "curl -f http://localhost:${local.aspect_ratio_inferrer_port}/healthcheck"]
        interval    = 30
        retries     = 3
        startPeriod = 30
        timeout     = 5
      }
    }
  }
}

# Allow the task to report success/failure back to the state machine.
data "aws_iam_policy_document" "inference_manager_task_token" {
  statement {
    effect  = "Allow"
    actions = ["states:SendTaskSuccess", "states:SendTaskFailure"]
    resources = [
      module.image_inferrer_state_machine.state_machine_arn,
    ]
  }
}

resource "aws_iam_role_policy" "inference_manager_task_token" {
  role   = module.inference_manager_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.inference_manager_task_token.json
}

# The Python manager reads ES credentials from Secrets Manager at runtime
# (catalogue_graph `get_secret`), so the task role needs read access to the
# pipeline-storage secrets (the Scala manager used injected env vars instead).
data "aws_iam_policy_document" "inference_manager_pipeline_storage_secret_read" {
  statement {
    effect  = "Allow"
    actions = ["secretsmanager:GetSecretValue"]
    resources = [
      "${local.secrets_manager_prefix}:elasticsearch/pipeline_storage_${var.pipeline_date}/*",
    ]
  }
}

resource "aws_iam_role_policy" "inference_manager_secret_read" {
  role   = module.inference_manager_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.inference_manager_pipeline_storage_secret_read.json
}

# Each task reads its partition (image ids written by find_work) from S3.
data "aws_iam_policy_document" "inference_manager_s3_read" {
  statement {
    effect  = "Allow"
    actions = ["s3:GetObject"]
    resources = [
      "arn:aws:s3:::wellcomecollection-catalogue-graph/inferrer/*",
      "arn:aws:s3:::wellcomecollection-catalogue-graph-dev/inferrer/*",
    ]
  }
}

resource "aws_iam_role_policy" "inference_manager_s3_read" {
  role   = module.inference_manager_ecs_task.task_role_name
  policy = data.aws_iam_policy_document.inference_manager_s3_read.json
}
