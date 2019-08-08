data "terraform_remote_state" "catalogue_infra_critical" {
  backend = "s3"

  config {
    role_arn = "arn:aws:iam::760097843905:role/platform-developer"

    bucket = "wellcomecollection-platform-infra"
    key    = "terraform/catalogue/infrastructure/critical.tfstate"
    region = "eu-west-1"
  }
}
