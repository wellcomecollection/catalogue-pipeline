provider "aws" {
  region  = var.aws_region
  version = "~> 2.7"

  assume_role {
    role_arn = "arn:aws:iam::756629837203:role/catalogue-developer"
  }
}

provider "aws" {
  alias = "platform"

  region  = var.aws_region
  version = "~> 2.7"

  assume_role {
    role_arn = "arn:aws:iam::760097843905:role/platform-developer"
  }
}

provider "aws" {
  alias = "dns"

  region  = var.aws_region
  version = "~> 2.7"

  assume_role {
    role_arn = "arn:aws:iam::267269328833:role/wellcomecollection-assume_role_hosted_zone_update"
  }
}
