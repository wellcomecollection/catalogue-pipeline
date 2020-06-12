data "aws_ssm_parameter" "critical_slack_webhook" {
  name = "/aws/reference/secretsmanager/sierra_adapter/critical_slack_webhook"
}

locals {
  namespace_hyphen = replace(var.namespace, "_", "-")

  sierra_api_url = "https://libsys.wellcomelibrary.org/iii/sierra-api/v3"

  sierra_items_fields = "updatedDate,createdDate,deletedDate,deleted,bibIds,location,status,barcode,callNumber,itemType,fixedFields,varFields"
  sierra_bibs_fields  = "updatedDate,createdDate,deletedDate,deleted,suppressed,available,lang,title,author,materialType,bibLevel,publishYear,catalogDate,country,orders,normTitle,normAuthor,locations,fixedFields,varFields"

  critical_slack_webhook = data.aws_ssm_parameter.critical_slack_webhook.value
  read_principles = [
    "arn:aws:iam::269807742353:root",
    "arn:aws:iam::964279923020:role/datascience_ec2",
    "arn:aws:iam::964279923020:root",
  ]
}
