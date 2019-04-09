data "aws_ssm_parameter" "critical_slack_webhook" {
  name = "/aws/reference/secretsmanager/sierra_adapter/critical_slack_webhook"
}

locals {
  sierra_api_url = "https://libsys.wellcomelibrary.org/iii/sierra-api/v3"

  sierra_items_fields = "updatedDate,createdDate,deletedDate,deleted,bibIds,location,status,barcode,callNumber,itemType,fixedFields,varFields"
  sierra_bibs_fields  = "updatedDate,createdDate,deletedDate,deleted,suppressed,available,lang,title,author,materialType,bibLevel,publishYear,catalogDate,country,orders,normTitle,normAuthor,locations,fixedFields,varFields"

  critical_slack_webhook = "${data.aws_ssm_parameter.critical_slack_webhook.value}"
}
