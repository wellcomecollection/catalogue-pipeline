resource "aws_security_group" "neptune_service_security_group" {
  name   = "catalogue-graph-neptune-interservice"
  vpc_id = data.aws_vpc.vpc.id
}
