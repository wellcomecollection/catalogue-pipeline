// this is required here in the child module to ensure correct resolution 
terraform {
  required_providers {
    github = {
      source = "integrations/github"
    }
  }
}

resource "github_actions_secret" "catalogue_graph_ci" {
  repository      = "catalogue-pipeline"
  secret_name     = "CATALOGUE_GRAPH_CI_ROLE_ARN"
  plaintext_value = module.gha_catalogue_graph_ci_role.role_arn
}
