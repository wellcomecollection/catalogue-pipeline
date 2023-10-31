# Note: this file is autogenerated by the run_terraform.sh script.
#
# Edits to this file may be reverted!

locals {
  pipeline_date = "2023-10-30"
}

terraform {
  backend "s3" {
    role_arn = "arn:aws:iam::760097843905:role/platform-developer"

    bucket         = "wellcomecollection-platform-infra"
    key            = "terraform/catalogue-pipeline/pipeline/2023-10-30.tfstate"
    dynamodb_table = "terraform-locktable"
    region         = "eu-west-1"
  }
}
