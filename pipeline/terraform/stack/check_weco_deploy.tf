# This is a crude check that you've remembered to run weco-deploy
# before running a new pipeline.  It looks for the tag created by
# weco-deploy in one of the ECR repositories.
#
# If you get an error telling you this tag can't be found, you probably
# forgot to do something in weco-deploy.
data "aws_ecr_image" "check_weco_deploy_tags_exist" {
  repository_name = "uk.ac.wellcome/transformer_tei"
  image_tag       = "env.${var.release_label}"
}
