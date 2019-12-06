package uk.ac.wellcome.mets_adapter.models

/** Represents an update received over SQS from the storage-service upon ingest
  *  of some data.
  */
case class IngestUpdate(context: IngestUpdateContext)

case class IngestUpdateContext(storageSpace: String, externalIdentifier: String)
