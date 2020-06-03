package uk.ac.wellcome.mets_adapter.models

// A notification from the storage service about a newly-registered bag.
//
// See https://github.com/wellcomecollection/storage-service/blob/0d46a8696bbc66d3c9c57b3d79217ac67faa4bee/common/src/main/scala/uk/ac/wellcome/platform/archive/common/BagRegistrationNotification.scala
case class BagRegistrationNotification(
  space: String,
  externalIdentifier: String
)
