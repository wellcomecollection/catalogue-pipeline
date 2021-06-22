resource "aws_ecr_repository" "ecr_repository_tei_id_extractor" {
  name = "uk.ac.wellcome/tei_id_extractor"
}
resource "aws_ecr_repository" "ecr_repository_tei_adapter" {
  name = "uk.ac.wellcome/tei_adapter"
}

locals {
  tei_id_extractor_image = "${aws_ecr_repository.ecr_repository_tei_id_extractor.repository_url}:env.${local.release_label}"
  tei_adapter_image = "${aws_ecr_repository.ecr_repository_tei_adapter.repository_url}:env.${local.release_label}"
}
