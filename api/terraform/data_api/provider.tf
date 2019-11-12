data "aws_caller_identity" "current" {}

provider "aws" {
  alias = "us_e1"
}

provider "aws" {
  alias = "platform_account"
}

provider "aws" {
  alias = "routemaster"
}
