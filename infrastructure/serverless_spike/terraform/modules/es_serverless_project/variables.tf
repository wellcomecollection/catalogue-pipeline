variable "name" {
  type = string
}

variable "region_id" {
  type    = string
  default = "aws-eu-west-1"
}

variable "traffic_filter_ids" {
  type    = set(string)
  default = []
}

variable "tags" {
  type    = map(string)
  default = {}
}
