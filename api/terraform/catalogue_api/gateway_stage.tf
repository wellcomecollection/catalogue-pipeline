locals {
  variables = {
    port = "${var.listener_port}"
  }
}

resource "aws_api_gateway_deployment" "stage" {
  rest_api_id = "${var.api_id}"

  # If we specify the stage name here then API Gateway tries to create it even
  # if it already exists (from below). Setting it to an empty string prevents this.
  # See https://github.com/terraform-providers/terraform-provider-aws/issues/2918#issuecomment-356684239
  stage_name = ""

  variables = "${local.variables}"

  # This forces a new deployment (which is necessary) when Gateway config changes
  stage_description = "${filemd5("gateway.tf")}"

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
