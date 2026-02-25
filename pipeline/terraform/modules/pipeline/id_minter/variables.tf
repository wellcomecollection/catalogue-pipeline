variable "pipeline_date" {
  type = string
}

variable "id_minter_vpc_config" {
    type = object({
        subnet_ids         = list(string)
        security_group_ids = list(string)
    })
}