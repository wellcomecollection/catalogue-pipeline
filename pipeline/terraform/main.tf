locals {
  pipelines = {
    "2023-06-09" = {
      listen_to_reindexer      = false
      scale_up_tasks           = false
      scale_up_elastic_cluster = false
      scale_up_id_minter_db    = false
      scale_up_matcher_db      = false

      index_config = {
        works = {
          identified = "works_identified.2023-05-26"
          merged     = "works_merged.2023-05-26"
          indexed    = "works_indexed.2023-05-26"
        }
        images = {
          indexed        = "images_indexed.2023-05-26"
          works_analysis = "works_indexed.2023-05-26"
        }
      }
    },
    "2023-06-26" = {
      listen_to_reindexer      = false
      scale_up_tasks           = false
      scale_up_elastic_cluster = false
      scale_up_id_minter_db    = false
      scale_up_matcher_db      = false

      index_config = {
        works = {
          identified = "works_identified.2023-05-26"
          merged     = "works_merged.2023-05-26"
          indexed    = "works_indexed.2023-05-26"
        }
        images = {
          indexed        = "images_indexed.2023-06-26"
          works_analysis = "works_indexed.2023-05-26"
        }
      }
    }
  }
}

module "pipelines" {
  source = "./modules/stack"

  for_each = local.pipelines

  pipeline_date = each.key
  release_label = each.key

  reindexing_state = {
    listen_to_reindexer      = each.value["listen_to_reindexer"]
    scale_up_tasks           = each.value["scale_up_tasks"]
    scale_up_elastic_cluster = each.value["scale_up_elastic_cluster"]
    scale_up_id_minter_db    = each.value["scale_up_id_minter_db"]
    scale_up_matcher_db      = each.value["scale_up_matcher_db"]
  }

  index_config = each.value["index_config"]

  providers = {
    aws.catalogue = aws.catalogue
  }
}

provider "aws" {
  region = "eu-west-1"

  assume_role {
    role_arn = "arn:aws:iam::760097843905:role/platform-admin"
  }
}

provider "aws" {
  region = "eu-west-1"

  alias = "catalogue"

  assume_role {
    role_arn = "arn:aws:iam::756629837203:role/catalogue-developer"
  }
}

provider "ec" {}

terraform {
  required_version = ">= 0.13"
  required_providers {
    aws = {
      source = "hashicorp/aws"
    }
    ec = {
      source  = "elastic/ec"
      version = "0.2.1"
    }
  }
}

terraform {
  backend "s3" {
    role_arn = "arn:aws:iam::760097843905:role/platform-developer"

    bucket         = "wellcomecollection-platform-infra"
    key            = "terraform/catalogue/pipeline.tfstate"
    dynamodb_table = "terraform-locktable"
    region         = "eu-west-1"
  }
}
