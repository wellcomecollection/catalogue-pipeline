# Create the Glue database that the Lambda will use
resource "aws_glue_catalog_database" "wellcomecollection_catalogue" {
  name        = "wellcomecollection_catalogue"
  description = "Database for Wellcome Collection catalogue data from adapters"
}
