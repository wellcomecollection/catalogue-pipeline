data "aws_ssm_parameter" "sierra_bib_merger" {
  name = "/sierra_adapter/images/latest/sierra_bib_merger"
}

data "aws_ssm_parameter" "sierra_item_merger" {
  name = "/sierra_adapter/images/latest/sierra_item_merger"
}

data "aws_ssm_parameter" "sierra_items_to_dynamo" {
  name = "/sierra_adapter/images/latest/sierra_items_to_dynamo"
}

data "aws_ssm_parameter" "sierra_reader" {
  name = "/sierra_adapter/images/latest/sierra_reader"
}

locals {
  sierra_bib_merger_image      = data.aws_ssm_parameter.sierra_bib_merger.value
  sierra_item_merger_image     = data.aws_ssm_parameter.sierra_item_merger.value
  sierra_items_to_dynamo_image = data.aws_ssm_parameter.sierra_items_to_dynamo.value
  sierra_reader_image          = data.aws_ssm_parameter.sierra_reader.value
}
