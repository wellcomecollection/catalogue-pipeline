locals {
  ec_privatelink_security_group_id = data.terraform_remote_state.shared_infra.outputs.ec_platform_privatelink_sg_id

  slack_webhook = "catalogue_graph_reporter/slack_webhook"

  vpc_id          = data.terraform_remote_state.platform_infra.outputs.catalogue_vpc_delta_id
  private_subnets = data.terraform_remote_state.platform_infra.outputs.catalogue_vpc_delta_private_subnets
  public_subnets  = data.terraform_remote_state.platform_infra.outputs.catalogue_vpc_delta_public_subnets

  lambda_vpc_config = {
    subnet_ids = local.private_subnets
    security_group_ids = [
      aws_security_group.graph_pipeline_security_group.id,
      local.ec_privatelink_security_group_id
    ]
  }

  ingestor_types = ["concepts", "works", "images"]

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
      "label" : "Wellcome Concept Nodes",
      "transformer_type" : "weco_concepts",
      "entity_type" : "nodes"
    },
    # There is deliberately no "Wellcome Concept Edges" entry. Those edges start at a catalogue
    # Concept node, which only the incremental pipeline creates, so they are extracted as part of
    # "Catalogue Concept Edges" below.
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

  graph_pipeline_inputs_incremental = [
    {
      "label" : "Catalogue Work Nodes",
      "transformer_type" : "catalogue_works",
      "entity_type" : "nodes"
    },
    {
      "label" : "Catalogue Work Identifier Nodes",
      "transformer_type" : "catalogue_work_identifiers",
      "entity_type" : "nodes"
    },
    {
      "label" : "Catalogue Concept Nodes",
      "transformer_type" : "catalogue_concepts",
      "entity_type" : "nodes"
    },
    {
      "label" : "Catalogue Image Nodes",
      "transformer_type" : "catalogue_images",
      "entity_type" : "nodes"
    },
    {
      "label" : "Catalogue Work Edges",
      "transformer_type" : "catalogue_works",
      "entity_type" : "edges"
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
      "label" : "Catalogue Concept Edges",
      "transformer_type" : "catalogue_concepts",
      "entity_type" : "edges",
      # When bulk loading concept edges, we are expecting a small number of insert failures due to missing
      # source concept nodes. Catalogue concepts are matched against the source ontology bulk load files in S3,
      # which the monthly pipeline refreshes hours before it loads the corresponding nodes into Neptune, so an
      # edge can reference a newly minted source concept which does not exist in the graph yet. Neptune drops
      # such edges; the edge is created the next time the referencing work is updated after the monthly load
      # completes.
      # When running in incremental mode, we cannot predict how many of these missing source concepts will exist
      # in any given batch, and so we allow any number of insert errors.
      "insert_error_threshold" : 1
    },
    {
      "label" : "Catalogue Image Edges",
      "transformer_type" : "catalogue_images",
      "entity_type" : "edges"
    },
  ]

  # Outer bound for waitForTaskToken ECS steps, sized for the monthly run. Liveness
  # is enforced by the heartbeat below.
  ecs_task_token_timeout_seconds = 12 * 60 * 60 # 12 hours

  # Tasks beat every 60s (HEARTBEAT_INTERVAL_SECONDS in utils/steps.py). The margin
  # covers cold start before the first beat: provisioning, image pull, interpreter.
  ecs_task_token_heartbeat_seconds = 5 * 60 # 5 minutes

  state_function_default_retry = [
    {
      # ErrorEquals is an exact, case-sensitive match, so the prefix is ECS., not Ecs.
      ErrorEquals = [
        "Lambda.ServiceException",
        "Lambda.AWSLambdaException",
        "Lambda.SdkClientException",
        "Lambda.TooManyRequestsException",
        "ECS.ServerException",
        "ECS.ThrottlingException",
        "ECS.TaskFailedToStartException",
        "ECS.CannotPullContainerErrorException",
        "ECS.ContainerRuntimeTimeoutErrorException",
        "ECS.EssentialContainerExited",
        "States.Timeout",
      ]
      IntervalSeconds = 1
      MaxAttempts     = 3
      BackoffRate     = 2
      JitterStrategy  = "FULL"
    }
  ]

  # Matched on its own so that a permanent NeptuneRequestError, and the poller's
  # fatal "Load failed", still fail on the first attempt. What counts as transient
  # is decided in catalogue_graph/src/clients/neptune_client.py.
  transient_neptune_retry = [
    {
      ErrorEquals     = ["TransientNeptuneError"]
      IntervalSeconds = 5
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


data "aws_ecr_repository" "unified_pipeline_task" {
  name = "uk.ac.wellcome/unified_pipeline_task"
}
