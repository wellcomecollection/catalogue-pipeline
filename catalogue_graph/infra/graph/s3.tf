# S3 bucket for storing files to be bulk loaded into the Neptune cluster
resource "aws_s3_bucket" "catalogue_graph_bucket" {
  bucket = "wellcomecollection-catalogue-graph"
}
