provider "aws" {
  region  = "${var.aws_region}"
  version = "1.57.0"

  assume_role {
    role_arn = "arn:aws:iam::756629837203:role/catalogue-developer"
  }
}

provider "aws" {
  alias   = "platform"
  region  = "${var.aws_region}"
  version = "1.57.0"

  assume_role {
    role_arn = "arn:aws:iam::760097843905:role/platform-developer"
  }
}

provider "aws" {
  version = "1.57.0"
  region  = "eu-west-1"
  alias   = "routemaster"

  assume_role {
    role_arn = "arn:aws:iam::250790015188:role/wellcomecollection-assume_role_hosted_zone_update"
  }
}
