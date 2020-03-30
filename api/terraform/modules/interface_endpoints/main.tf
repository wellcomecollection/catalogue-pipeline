module "cloudwatch_endpoint" {
  source  = "./endpoint"
  service = "monitoring"

  security_group_ids = [var.security_group_id]
  subnet_ids         = var.subnet_ids
  vpc_id             = var.vpc_id
}

module "events_endpoint" {
  source  = "./endpoint"
  service = "events"

  security_group_ids = [var.security_group_id]
  subnet_ids         = var.subnet_ids
  vpc_id             = var.vpc_id
}

module "logs_endpoint" {
  source  = "./endpoint"
  service = "logs"

  security_group_ids = [var.security_group_id]
  subnet_ids         = var.subnet_ids
  vpc_id             = var.vpc_id
}
