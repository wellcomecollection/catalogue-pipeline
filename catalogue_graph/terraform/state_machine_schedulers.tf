resource "aws_scheduler_schedule" "concept_pipeline_monthly" {
  name = "concept_pipeline_monthly_run"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression = "20 9 ? 1/1 MON#1 *" # 1st Monday of the month at 9:20am

  target {
    arn      = aws_sfn_state_machine.concepts_pipeline.arn
    role_arn = aws_iam_role.state_machine_execution_role.arn

    input = jsonencode({
      MessageBody = local.concepts_pipeline_monthly_input
    })
  }
}

resource "aws_scheduler_schedule" "concept_pipeline_daily" {
  name = "concept_pipeline_daily_run"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression = "20 14 ? * MON-THU *" # MON-THU 2:20pm

  target {
    arn      = aws_sfn_state_machine.concepts_pipeline.arn
    role_arn = aws_iam_role.state_machine_execution_role.arn

    input = jsonencode({
      MessageBody = local.concept_pipeline_daily_input
    })
  }
}
locals {
    concepts_pipeline_monthly_input = [ 
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
      "label" : "Wikidata Linked LoC Concept Edges",
      "transformer_type" : "wikidata_linked_loc_concepts",
      "entity_type" : "edges"
    },
    {
      "label" : "Wikidata Linked LoC Location Edges",
      "transformer_type" : "wikidata_linked_loc_locations",
      "entity_type" : "edges"
    },
    {
      "label" : "Wikidata Linked LoC Name Edges",
      "transformer_type" : "wikidata_linked_loc_names",
      "entity_type" : "edges"
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
      "label" : "Wikidata Linked MeSH Concept Edges",
      "transformer_type" : "wikidata_linked_mesh_concepts",
      "entity_type" : "edges"
    },
    {
      "label" : "Wikidata Linked MeSH Location Edges",
      "transformer_type" : "wikidata_linked_mesh_locations",
      "entity_type" : "edges"
    },
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

  concept_pipeline_daily_input = [
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
}