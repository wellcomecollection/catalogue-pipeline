variable "vpc_id" {}

variable "subnets" {
  type = list(string)
}

variable "ecs_cluster_id" {}
variable "service_name" {}
variable "task_definition_arn" {}
variable "container_port" {}

variable "launch_type" {
  default = "FARGATE"
}

variable "task_desired_count" {
  type = number
  default = 1
}

variable "deployment_minimum_healthy_percent" {
  type = number
  default = 100
}

variable "deployment_maximum_percent" {
  type = number
  default = 200
}

variable "security_group_ids" {
  type = list(string)
}

variable "assign_public_ip" {
  type = bool
  default = false
}

variable "container_name" {}
variable "lb_arn" {}
variable "listener_port" {}
variable "namespace_id" {}

variable "target_group_deregistration_delay" {
  type = number
  default = 300
}

variable "service_discovery_failure_threshold" {
  type = number
  default = 1
}

variable "deregistration_delay" {
  type = number
  default = 300
}
