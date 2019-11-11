provider "aws" {
  region  = "${var.aws_region}"
  version = "1.57.0"

  assume_role {
    role_arn = "arn:aws:iam::756629837203:role/catalogue-developer"
  }
}

// Given the tangle of resources in this repo, the lightest touch
// approach to having the API in the `catalogue` VPC is to allow access
// to select resources in the `platform` account.
provider "aws" {
  alias   = "platform_account"
  region  = "${var.aws_region}"
  version = "1.57.0"

  assume_role {
    role_arn = "arn:aws:iam::760097843905:role/platform-developer"
  }
}
