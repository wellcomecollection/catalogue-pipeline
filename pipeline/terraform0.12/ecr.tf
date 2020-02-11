resource "aws_ecr_repository" "ecr_repository_nginx_services" {
  name = "uk.ac.wellcome/nginx_services"
}

resource "aws_ecr_repository" "ecr_repository_transformer_miro" {
  name = "uk.ac.wellcome/transformer_miro"
}

resource "aws_ecr_repository" "ecr_repository_transformer_sierra" {
  name = "uk.ac.wellcome/transformer_sierra"
}

resource "aws_ecr_repository" "ecr_repository_transformer_mets" {
  name = "uk.ac.wellcome/transformer_mets"
}

resource "aws_ecr_repository" "ecr_repository_id_minter" {
  name = "uk.ac.wellcome/id_minter"
}

resource "aws_ecr_repository" "ecr_repository_recorder" {
  name = "uk.ac.wellcome/recorder"
}

resource "aws_ecr_repository" "ecr_repository_matcher" {
  name = "uk.ac.wellcome/matcher"
}

resource "aws_ecr_repository" "ecr_repository_merger" {
  name = "uk.ac.wellcome/merger"
}

resource "aws_ecr_repository" "ecr_repository_ingestor" {
  name = "uk.ac.wellcome/ingestor"
}

resource "aws_ecr_repository" "ecr_repository_elasticdump" {
  name = "uk.ac.wellcome/elasticdump"
}
