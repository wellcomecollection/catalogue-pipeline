provider "aws" {
  region  = var.aws_region
  version = "2.60.0"

  # Ignore deployment tags on services
  ignore_tags {
    keys = ["deployment:label"]
  }

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
