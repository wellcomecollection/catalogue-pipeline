# DEACTIVATED - 2024-12-16
# This pipeline has been deactivated over the winter break,
# to save costs and will be reactivated in the new year.
# We need to keep this folder, so that new deploys don't go
# to the production environment.

# module "pipeline" {
#   source = "../modules/stack"
#
#   reindexing_state = {
#     listen_to_reindexer      = false
#     scale_up_tasks           = false
#     scale_up_elastic_cluster = false
#     scale_up_id_minter_db    = false
#     scale_up_matcher_db      = false
#   }
#
#   index_config = {
#     works = {
#       identified = "works_identified.2023-05-26"
#       merged     = "works_merged.2023-05-26"
#       indexed    = "works_indexed.2024-11-14"
#     }
#     images = {
#       indexed        = "images_indexed.2024-11-14"
#       works_analysis = "works_indexed.2024-11-06"
#     }
#   }
#
#   allow_delete_indices = true
#
#   pipeline_date = local.pipeline_date
#   release_label = local.pipeline_date
#
#   providers = {
#     aws.catalogue = aws.catalogue
#   }
# }
