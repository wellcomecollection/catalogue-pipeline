resource "null_resource" "ecr_image_tags" {
  triggers = {
    pipeline_date = var.pipeline_date
  }

  provisioner "local-exec" {
    command = "PIPELINE_DATE=${var.pipeline_date} bash scripts/ensure_ecr_tags_exist_for_pipeline.sh"
  }
}
