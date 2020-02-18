provider "aws" {
  region = var.aws_region

  assume_role {
    role_arn = local.catalogue_developer_role_arn
  }
}

provider "aws" {
  alias  = "us_e1"
  region = "us-east-1"

  assume_role {
    role_arn = local.catalogue_developer_role_arn
  }
}

// Given the tangle of resources in this repo, the lightest touch
// approach to having the API in the `catalogue` VPC is to allow access
// to select resources in the `platform` account.
provider "aws" {
  alias  = "platform_account"
  region = var.aws_region

  assume_role {
    role_arn = local.platform_developer_role_arn
  }
}

provider "aws" {
  region = "eu-west-1"
  alias  = "routemaster"

  assume_role {
    role_arn = "arn:aws:iam::250790015188:role/wellcomecollection-assume_role_hosted_zone_update"
  }
}

