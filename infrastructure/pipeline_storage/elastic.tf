locals {
  traffic_filter_platform_vpce_id  = data.terraform_remote_state.infra_critical.outputs["traffic_filter_platform_vpce_id"]
  traffic_filter_public_internet_id = data.terraform_remote_state.infra_critical.outputs["traffic_filter_public_internet_id"]
}

resource "ec_deployment" "pipeline_storage" {
  name = "catalogue-pipeline-storage-tf-managed"

  region                 = "eu-west-1"
  version                = "7.10.2"
  deployment_template_id = "aws-io-optimized-v2"

  traffic_filter = [
    local.traffic_filter_platform_vpce_id,
    local.traffic_filter_public_internet_id,
  ]

  elasticsearch {
    topology {
      zone_count = 1
      size       = "8g"
    }
  }

  kibana {
    topology {
      zone_count = 1
      size       = "1g"
    }
  }
}

locals {
  pipeline_storage_elastic_id     = ec_deployment.pipeline_storage.elasticsearch[0].resource_id
  pipeline_storage_elastic_region = ec_deployment.pipeline_storage.elasticsearch[0].region
}

module "pipeline_storage_secrets" {
  source = "../modules/secrets"

  key_value_map = {
    "elasticsearch/pipeline_storage_delta/public_host" = "${local.pipeline_storage_elastic_id}.${local.pipeline_storage_elastic_region}.aws.found.io"

    # See https://www.elastic.co/guide/en/cloud/current/ec-traffic-filtering-vpc.html
    "elasticsearch/pipeline_storage_delta/private_host" = "${local.pipeline_storage_elastic_id}.vpce.${local.pipeline_storage_elastic_region}.aws.elastic-cloud.com"
  }
}
