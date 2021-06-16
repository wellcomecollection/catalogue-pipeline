resource "aws_ecr_repository" "ecr_repository_tei_id_extractor" {
  name = "uk.ac.wellcome/tei_id_extractor"
}

locals {
  tei_id_extractor_image = "${aws_ecr_repository.ecr_repository_tei_id_extractor.repository_url}:env.${local.release_label}"
}
