variable "infra_bucket" {
  default = "wellcomecollection-platform-infra"
}

variable "namespace" {
  default = "sierra-adapter"
}

# Set false to hold bib changes out of the catalogue while items keep flowing; the progress reporter skips bibs meanwhile.
# To catch up, re-enable first: build_missing_windows.py only sees a gap once a newer bib window closes it.
variable "sierra_bibs_updates_enabled" {
  type    = bool
  default = true
}
