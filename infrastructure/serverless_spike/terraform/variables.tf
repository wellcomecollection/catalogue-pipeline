variable "attach_private_policy" {
  type        = bool
  default     = false
  description = "Attach the PrivateLink policy to the project. Off for the public phase; once any policy is attached, unmatched sources get 403."
}

variable "allowed_ip_cidrs" {
  type        = list(string)
  default     = []
  description = "CIDRs allowed through an IP policy alongside the private one, for example a laptop running the harness."
}

variable "search_power" {
  type        = number
  default     = null
  description = "Project search power, 28 to 3000. The console did not offer the tier labels for this project, so the floor stands in for On-demand."
}
