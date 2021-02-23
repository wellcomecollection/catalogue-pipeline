package uk.ac.wellcome.sierra_adapter.model

import java.time.Instant

trait AbstractSierraRecord[Id <: SierraTypedRecordNumber] {
  val id: Id
  val data: String
  val modifiedDate: Instant
}
