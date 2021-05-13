resource "aws_ecr_repository" "ecr_repository_tei_github" {
  name = "uk.ac.wellcome/tei_github"
}

locals {
  tei_github_image = "${aws_ecr_repository.ecr_repository_tei_github.repository_url}:env.${local.release_label}"
}
