resource "aws_ecs_cluster" "pipeline_cluster" {
  name = "unified-pipeline-${var.pipeline_date}"

  setting {
    name  = "containerInsights"
    value = "enhanced"
  }
}
