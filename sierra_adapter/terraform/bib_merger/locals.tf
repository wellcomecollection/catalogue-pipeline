locals {
  resource_type_singular = "${replace("${var.resource_type}", "s", "")}"
}
