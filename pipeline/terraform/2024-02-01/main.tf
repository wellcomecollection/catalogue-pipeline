data "aws_ami" "container_host_ami" {
  most_recent = true
  owners      = ["self"]
  filter {
    name   = "name"
    values = ["weco-amzn2-ecs-optimised-hvm-x86_64*"]
  }
}

module "pipeline" {
  source = "../modules/stack"

  reindexing_state = {
    listen_to_reindexer      = false
    scale_up_tasks           = false
    scale_up_elastic_cluster = false
    scale_up_id_minter_db    = false
    scale_up_matcher_db      = false
  }

  index_config = {
    works = {
      identified = "works_identified.2023-05-26"
      merged     = "works_merged.2023-05-26"
      indexed    = "works_indexed.2024-01-09"
    }
    images = {
      indexed        = "images_indexed.2024-01-09"
      works_analysis = "works_indexed.2024-01-09"
    }
  }

  pipeline_date = local.pipeline_date
  release_label = local.pipeline_date

  providers = {
    aws.catalogue = aws.catalogue
  }
  
  ami_id = data.aws_ami.container_host_ami.image_id
}
