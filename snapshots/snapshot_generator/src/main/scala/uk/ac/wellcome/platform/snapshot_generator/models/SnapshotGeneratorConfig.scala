package uk.ac.wellcome.platform.snapshot_generator.models

import com.sksamuel.elastic4s.Index

case class SnapshotGeneratorConfig(
  index: Index,
  // How many documents should be fetched in a single request?
  //
  //  - If this value is too small, we have to make extra requests and
  //    snapshot creation will be slower.
  //
  //  - If this value is too big, we may exceed the heap memory on a single
  //    request -- >100MB in one set of returned works, and we get an error:
  //
  //        org.apache.http.ContentTooLongException: entity content is too
  //        long [167209080] for the configured buffer limit [104857600]
  //
  bulkSize: Int = 1000
)
