output "snapshots_bucket_arn" {
  value = "${module.data_api.snapshots_bucket_arn}"
}

output "catalogue_api_nlb_arn" {
  value = "${module.nlb.arn}"
}
