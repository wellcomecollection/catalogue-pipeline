package weco.catalogue.sierra_adapter.models

import java.time.Instant

trait AbstractSierraRecord[Id <: TypedSierraRecordNumber] {
  val id: Id
  val data: String
  val modifiedDate: Instant
}
