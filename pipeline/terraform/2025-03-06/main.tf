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
      indexed    = "works_indexed.2024-11-14"
    }
    images = {
      indexed        = "images_indexed.2024-11-14"
      works_analysis = "works_indexed.2024-11-06"
    }
    concepts = {
      # Define a set of concept indexes, each with its own config definition
      indexed = {
        "2025-03-06" = "concepts_indexed.2025-03-10"
        "2025-04-24" = "concepts_indexed.2025-03-10"
      }
    }
  }

  allow_delete_indices = false

  pipeline_date = local.pipeline_date
  release_label = local.pipeline_date

  providers = {
    aws.catalogue = aws.catalogue
  }
}

# To prevent the 2025-03-06 concepts index from being destroyed and recreated, we need to explicitly tell Terraform
# it has moved to a different address.
# This bit won't be necessary in any newly created pipeline stacks.
moved {
  from = module.pipeline.module.pipeline_indices.module.concepts_indexed_index.elasticstack_elasticsearch_index.the_index
  to   = module.pipeline.module.pipeline_indices.module.concepts_indexed_indexes["2025-03-06"].elasticstack_elasticsearch_index.the_index
}


