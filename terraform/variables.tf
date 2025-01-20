# Each entry corresponds to a single execution of the `extractor` and `bulk_loader` Lambda functions. The `extractor`
# Lambda function will output a single S3 file, which will be loaded into the database via the `bulk_loader` Lambda function.
variable "state_machine_inputs" {
  type    = list(object({ label : string, transformer_type : string, entity_type : string }))
  default = [
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
    }
  ]
}
