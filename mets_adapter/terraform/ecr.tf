resource "aws_ecr_repository" "mets_adapter" {
  name = "uk.ac.wellcome/mets_adapter"

  lifecycle {
    prevent_destroy = true
  }
}
