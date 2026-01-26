locals {
  namespace_hyphen = replace(var.namespace, "_", "-")

  # See https://sandbox.iii.com/iii/sierra-api/swagger/index.html

  sierra_items_fields = join(",", [
    "updatedDate",
    "createdDate",
    "deletedDate",
    "deleted",
    "suppressed",
    "bibIds",
    "location",
    "status",
    "barcode",
    "callNumber",
    "itemType",
    "transitInfo",
    "copyNo",
    "holdCount",
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

  # See https://techdocs.iii.com/sierraapi/Content/zReference/objects/orderObject.htm
  sierra_orders_fields = join(",", [
    "bibs",
    "updatedDate",
    "createdDate",
    "deletedDate",
    "deleted",
    "suppressed",
    "accountingUnit",
    "estimatedPrice",
    "vendorRecordCode",
    "orderDate",
    "chargedFunds",
    "vendorTitles",
    "fixedFields",
    "varFields",
  ])

  fargate_service_boilerplate = {
    cluster_name = aws_ecs_cluster.cluster.name
    cluster_arn  = aws_ecs_cluster.cluster.arn

    dlq_alarm_topic_arn = var.dlq_alarm_arn

    subnets = var.private_subnets

    elastic_cloud_vpce_security_group_id = var.elastic_cloud_vpce_sg_id

    shared_logging_secrets = var.shared_logging_secrets

    egress_security_group_id = var.egress_security_group_id
    # Empty namespace, prevents exceeding name length limits
    namespace = ""
  }
}
