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

variable "search_power" {
  type        = number
  default     = null
  description = "28 to 3000; null leaves the project default of 100."
}

variable "boost_window" {
  type        = number
  default     = null
  description = "1 to 180 days; null leaves the project default (7)."
}
