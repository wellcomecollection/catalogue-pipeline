locals {
  namespace = "graph-pipeline"

  _extractor_task_definition_split     = split(":", module.extractor_ecs_task.task_definition_arn)
  extractor_task_definition_version    = element(local._extractor_task_definition_split, length(local._extractor_task_definition_split) - 1)
  extractor_task_definition_arn_latest = trimsuffix(module.extractor_ecs_task.task_definition_arn, ":${local.extractor_task_definition_version}")

  ec_privatelink_security_group_id = data.terraform_remote_state.shared_infra.outputs.ec_platform_privatelink_sg_id

  slack_webhook = "catalogue_graph_reporter/slack_webhook"

  vpc_id          = data.terraform_remote_state.platform_infra.outputs.catalogue_vpc_delta_id
  private_subnets = data.terraform_remote_state.platform_infra.outputs.catalogue_vpc_delta_private_subnets
  public_subnets  = data.terraform_remote_state.platform_infra.outputs.catalogue_vpc_delta_public_subnets

  ingestor_types = ["concepts", "works"]

  bulk_loader_default_insert_error_threshold = 1 / 10000

  concepts_pipeline_inputs_monthly = [
    {
      "label" : "LoC Concept Nodes",
      "transformer_type" : "loc_concepts",
      "entity_type" : "nodes"
    },
    {
      "label" : "LoC Location Nodes",
      "transformer_type" : "loc_locations",
      "entity_type" : "nodes"
    },
    {
      "label" : "LoC Name Nodes",
      "transformer_type" : "loc_names",
      "entity_type" : "nodes"
    },
    {
      "label" : "LoC Concept Edges",
      "transformer_type" : "loc_concepts",
      "entity_type" : "edges"
    },
    {
      "label" : "LoC Location Edges",
      "transformer_type" : "loc_locations",
      "entity_type" : "edges"
    },
    {
      "label" : "MeSH Concept Nodes",
      "transformer_type" : "mesh_concepts",
      "entity_type" : "nodes"
    },
    {
      "label" : "MeSH Location Nodes",
      "transformer_type" : "mesh_locations",
      "entity_type" : "nodes"
    },
    {
      "label" : "MeSH Concept Edges",
      "transformer_type" : "mesh_concepts",
      "entity_type" : "edges"
    },
    {
      "label" : "Wikidata Linked LoC Concept Nodes",
      "transformer_type" : "wikidata_linked_loc_concepts",
      "entity_type" : "nodes"
    },
    {
      "label" : "Wikidata Linked LoC Location Nodes",
      "transformer_type" : "wikidata_linked_loc_locations",
      "entity_type" : "nodes"
    },
    {
      "label" : "Wikidata Linked LoC Name Nodes",
      "transformer_type" : "wikidata_linked_loc_names",
      "entity_type" : "nodes"
    },
    {
      "label" : "Wikidata Linked MeSH Concept Nodes",
      "transformer_type" : "wikidata_linked_mesh_concepts",
      "entity_type" : "nodes"
    },
    {
      "label" : "Wikidata Linked MeSH Location Nodes",
      "transformer_type" : "wikidata_linked_mesh_locations",
      "entity_type" : "nodes"
    },
    {
      "label" : "Wikidata Linked LoC Concept Edges",
      "transformer_type" : "wikidata_linked_loc_concepts",
      "entity_type" : "edges",
      "insert_error_threshold" : 1 / 2000
    },
    {
      "label" : "Wikidata Linked LoC Location Edges",
      "transformer_type" : "wikidata_linked_loc_locations",
      "entity_type" : "edges",
      "insert_error_threshold" : 1 / 2000
    },
    {
      "label" : "Wikidata Linked LoC Name Edges",
      "transformer_type" : "wikidata_linked_loc_names",
      "entity_type" : "edges",
      "insert_error_threshold" : 1 / 2000
    },
    {
      "label" : "Wikidata Linked MeSH Concept Edges",
      "transformer_type" : "wikidata_linked_mesh_concepts",
      "entity_type" : "edges",
      "insert_error_threshold" : 1 / 2000
    },
    {
      "label" : "Wikidata Linked MeSH Location Edges",
      "transformer_type" : "wikidata_linked_mesh_locations",
      "entity_type" : "edges",
      "insert_error_threshold" : 1 / 2000
    }
  ]

  concepts_pipeline_inputs_incremental = [
    {
      "label" : "Catalogue Concept Nodes",
      "transformer_type" : "catalogue_concepts",
      "entity_type" : "nodes"
    },
    {
      "label" : "Catalogue Concept Edges",
      "transformer_type" : "catalogue_concepts",
      "entity_type" : "edges"
    },
    {
      "label" : "Catalogue Work Identifier Nodes",
      "transformer_type" : "catalogue_work_identifiers",
      "entity_type" : "nodes"
    },
    {
      "label" : "Catalogue Work Identifier Edges",
      "transformer_type" : "catalogue_work_identifiers",
      "entity_type" : "edges",
      # When bulk loading work identifier edges, we are expecting a small number of insert failures due to missing
      # parent nodes. This is because some extracted parent_path_identifier values do not exist in the collection.
      # (For example, we might have a child path identifier 'A/B/123' for which we extract the parent identifier 'A/B',
      # but there is no guarantee that a work with this identifier exists.)
      # When running in incremental mode, we cannot predict how many of these missing path identifiers will exist
      # in any given batch, and so we allow any number of insert errors.
      "insert_error_threshold" : 1
    },
    {
      "label" : "Catalogue Work Nodes",
      "transformer_type" : "catalogue_works",
      "entity_type" : "nodes"
    },
    {
      "label" : "Catalogue Work Edges",
      "transformer_type" : "catalogue_works",
      "entity_type" : "edges"
    },
  ]

  state_function_default_retry = [
    {
      ErrorEquals = [
        "Lambda.ServiceException",
        "Lambda.AWSLambdaException",
        "Lambda.SdkClientException",
        "Lambda.TooManyRequestsException",
        "Ecs.ServerException",
        "Ecs.ThrottlingException",
        "Ecs.TaskFailedToStartException",
        "Ecs.CannotPullContainerErrorException",
        "Ecs.ContainerRuntimeTimeoutErrorException",
        "Ecs.EssentialContainerExited",
      ]
      IntervalSeconds = 1
      MaxAttempts     = 3
      BackoffRate     = 2
      JitterStrategy  = "FULL"
    }
  ]
}

data "aws_vpc" "vpc" {
  id = local.vpc_id
}

data "aws_s3_bucket" "catalogue_graph_bucket" {
  bucket = "wellcomecollection-catalogue-graph"
}

data "aws_ecr_repository" "unified_pipeline_lambda" {
  name = "uk.ac.wellcome/unified_pipeline_lambda"
}

data "aws_ecr_repository" "catalogue_graph_extractor" {
  name = "uk.ac.wellcome/catalogue_graph_extractor"
}
