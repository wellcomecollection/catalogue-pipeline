package uk.ac.wellcome.sierra_adapter.model

import java.time.Instant

trait AbstractSierraRecord {
  val id: SierraTypedRecordNumber
  val data: String
  val modifiedDate: Instant
}
