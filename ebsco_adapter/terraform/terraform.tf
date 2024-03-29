terraform {
  required_version = ">= 0.9"

  backend "s3" {
    role_arn = "arn:aws:iam::760097843905:role/platform-developer"

    bucket         = "wellcomecollection-platform-infra"
    key            = "terraform/ebsco_adapter.tfstate"
    dynamodb_table = "terraform-locktable"
    region         = "eu-west-1"
  }
}
