locals {
  variables = {
    port = "${var.listener_port}"
  }
}

resource "aws_api_gateway_deployment" "stage" {
  rest_api_id = "${var.api_id}"
  stage_name  = ""
  variables   = "${local.variables}"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_api_gateway_stage" "stage" {
  stage_name    = "${var.environment}"
  rest_api_id   = "${var.api_id}"
  deployment_id = "${aws_api_gateway_deployment.stage.id}"
  variables     = "${local.variables}"
}
