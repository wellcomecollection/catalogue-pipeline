variable "infra_bucket" {
  default = "wellcomecollection-platform-infra"
}

variable "namespace" {
  default = "sierra-adapter"
}

# Set false to hold bib changes out of the catalogue while items keep flowing; catch up by reharvesting the missed bib windows.
variable "sierra_bibs_updates_enabled" {
  type    = bool
  default = true
}
