provider "aws" {
  region = "eu-west-1"

  assume_role {
    role_arn = "arn:aws:iam::760097843905:role/platform-developer"
  }

  default_tags {
    tags = {
      TerraformConfigurationURL = "https://github.com/wellcomecollection/catalogue-pipeline/tree/main/infrastructure"
      Department                = "Digital Platform"
    }
  }
}
