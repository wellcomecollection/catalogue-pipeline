data "aws_ssm_parameter" "critical_slack_webhook" {
  name = "/aws/reference/secretsmanager/sierra_adapter/critical_slack_webhook"
}

locals {
  namespace_hyphen = replace(var.namespace, "_", "-")

  sierra_api_url = "https://libsys.wellcomelibrary.org/iii/sierra-api/v5"

  sierra_items_fields = join(",", [
    "updatedDate",
    "createdDate",
    "deletedDate",
    "deleted",
    "bibIds",
    "location",
    "status",
    "barcode",
    "callNumber",
    "itemType",
    "fixedFields",
    "varFields"
  ])

  sierra_bibs_fields = join(",", [
    "updatedDate",
    "createdDate",
    "deletedDate",
    "deleted",
    "suppressed",
    "available",
    "lang",
    "title",
    "author",
    "materialType",
    "bibLevel",
    "publishYear",
    "catalogDate",
    "country",
    "orders",
    "normTitle",
    "normAuthor",
    "locations",
    "fixedFields",
    "varFields"
  ])

  # See https://techdocs.iii.com/sierraapi/Content/zReference/objects/holdingsObjectDescription.htm
  sierra_holdings_fields = join(",", [
    "bibIds",
    "itemIds",
    "inheritLocation",
    "allocationRule",
    "accountingUnit",
    "labelCode",
    "serialCode1",
    "serialCode2",
    "claimOnDate",
    "receivingLocationCode",
    "vendorCode",
    "serialCode3",
    "serialCode4",
    "updateCount",
    "pieceCount",
    "eCheckInCode",
    "mediaTypeCode",
    "updatedDate",
    "createdDate",
    "deletedDate",
    "deleted",
    "suppressed",
    "fixedFields",
    "varFields",
  ])

  critical_slack_webhook = data.aws_ssm_parameter.critical_slack_webhook.value
}
