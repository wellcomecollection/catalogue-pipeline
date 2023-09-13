resource "null_resource" "ecr_image_tags" {
  triggers = {
    pipeline_date = var.pipeline_date
  }

  provisioner "local-exec" {
    # Use the root terraform directory, not the individual stacks' roots, as the working dir
    working_dir = "${path.root}/.."
    command     = "AWS_PROFILE=platform-developer PIPELINE_DATE=${var.pipeline_date} bash scripts/ensure_ecr_tags_exist_for_pipeline.sh"
  }
}
