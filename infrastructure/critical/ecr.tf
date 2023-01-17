resource "aws_ecr_repository" "nginx_services" {
  name = "uk.ac.wellcome/nginx_services"
}

resource "aws_ecr_repository" "transformer_miro" {
  name = "uk.ac.wellcome/transformer_miro"
}

resource "aws_ecr_repository" "transformer_sierra" {
  name = "uk.ac.wellcome/transformer_sierra"
}

resource "aws_ecr_repository" "transformer_mets" {
  name = "uk.ac.wellcome/transformer_mets"
}

resource "aws_ecr_repository" "transformer_calm" {
  name = "uk.ac.wellcome/transformer_calm"
}

resource "aws_ecr_repository" "transformer_tei" {
  name = "uk.ac.wellcome/transformer_tei"
}

resource "aws_ecr_repository" "id_minter" {
  name = "uk.ac.wellcome/id_minter"
}

resource "aws_ecr_repository" "matcher" {
  name = "uk.ac.wellcome/matcher"
}

resource "aws_ecr_repository" "merger" {
  name = "uk.ac.wellcome/merger"
}

resource "aws_ecr_repository" "ingestor_works" {
  name = "uk.ac.wellcome/ingestor_works"
}

resource "aws_ecr_repository" "inference_manager" {
  name = "uk.ac.wellcome/inference_manager"
}

resource "aws_ecr_repository" "feature_inferrer" {
  name = "uk.ac.wellcome/feature_inferrer"
}

resource "aws_ecr_repository" "feature_training" {
  name = "uk.ac.wellcome/feature_training"
}

resource "aws_ecr_repository" "palette_inferrer" {
  name = "uk.ac.wellcome/palette_inferrer"
}

resource "aws_ecr_repository" "aspect_ratio_inferrer" {
  name = "uk.ac.wellcome/aspect_ratio_inferrer"
}

resource "aws_ecr_repository" "ingestor_images" {
  name = "uk.ac.wellcome/ingestor_images"
}

resource "aws_ecr_repository" "elasticdump" {
  name = "uk.ac.wellcome/elasticdump"
}

resource "aws_ecr_repository" "relation_embedder" {
  name = "uk.ac.wellcome/relation_embedder"
}

resource "aws_ecr_repository" "router" {
  name = "uk.ac.wellcome/router"
}

resource "aws_ecr_repository" "batcher" {
  name = "uk.ac.wellcome/batcher"
}
